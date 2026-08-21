/*
 * cat_compiler.c - Компилятор проектов NewCode (Catrobat XML -> C -> машинный код).
 *
 * Схема работы:
 *   code.xml -> (cat_loader) -> CatProject -> (этот файл) -> prog.c
 *   prog.c + nc_rt.h -> cc -O2 -> нативный исполняемый файл.
 *
 * Свойства сгенерированной программы:
 *   - весь код проекта становится настоящим C: Repeat -> for, Forever ->
 *     while(1), If -> if, формулы -> выражения с NcVal-хелперами;
 *   - break/continue/return компилируются в машинные C-операторы;
 *   - malloc/calloc/realloc/free/memcpy/memset и разыменование указателей
 *     работают с реальной памятью процесса (явное управление, без GC);
 *   - значения (NcVal) и списки (NcList) живут на стеке/в статике —
 *     сборщика мусора нет в принципе;
 *   - клоны спрайтов: фиксированный пул поз (NC_CLONE_CAP на спрайт),
 *     поза копируется побитно, скрипты/переменные общие — ноль strdup,
 *     ноль роста кучи, клоны не лагают.
 */
#include "cat_compiler.h"
#include "cat_mem.h"

#include "nc_rt_data.h"

#include <ctype.h>
#include <math.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

/* ------------------------------------------------------------------ */
/* Строковый буфер генератора                                          */
/* ------------------------------------------------------------------ */

typedef struct {
    char  *buf;
    size_t len, cap;
    int    indent;
    int    counter; /* счётчик уникальных времённых имён */
} SB;

static void sb_putc(SB *g, char c) {
    if (g->len + 2 > g->cap) {
        g->cap = g->cap ? g->cap * 2 : 4096;
        g->buf = (char *)cat_realloc(g->buf, g->cap);
    }
    g->buf[g->len++] = c;
    g->buf[g->len] = 0;
}

static void sb_puts(SB *g, const char *s) {
    while (*s) sb_putc(g, *s++);
}

static void sb_printf(SB *g, const char *fmt, ...) {
    char tmp[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(tmp, sizeof tmp, fmt, ap);
    va_end(ap);
    sb_puts(g, tmp);
}

static void sb_indent(SB *g) {
    for (int i = 0; i < g->indent; ++i) sb_puts(g, "    ");
}

static void sb_line(SB *g, const char *fmt, ...) {
    char tmp[2048];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(tmp, sizeof tmp, fmt, ap);
    va_end(ap);
    sb_indent(g);
    sb_puts(g, tmp);
    sb_putc(g, '\n');
}

/* ------------------------------------------------------------------ */
/* Состояние компилятора                                               */
/* ------------------------------------------------------------------ */

typedef struct {
    char *name;
} NcName;

typedef struct {
    char *alias;
    char *base;
} NcTypedef;

typedef struct {
    char *msg;
    int  first_script; /* индекс в списке скриптов-получателей */
} NcBroadcast;

typedef struct {
    CatProject *p;
    SB          out;

    NcName    *vars;
    size_t     var_count, var_cap;

    NcName    *lists;
    size_t     list_count, list_cap;

    NcTypedef *tds;
    size_t     td_count, td_cap;

    /* объявления составных типов (struct/enum) — выводятся до кода */
    char     **type_decls;
    size_t     type_decl_count, type_decl_cap;

    /* метки goto, объявленные блоками LabelBrick */
    char     **labels;
    size_t     label_count, label_cap;

    CatSprite *sprite;   /* текущий спрайт */
    int        sprite_i; /* его индекс */
    int        loop_depth;

    /* по плоскому индексу спрайта: число скриптов «когда я клон» */
    int       *clone_scripts;
    size_t     clone_scripts_n;

    /* таблица broadcast-сообщений */
    char    **bmsg;
    size_t    bmsg_count, bmsg_cap;
} NcGen;

static int nc_name_find(NcName *arr, size_t n, const char *name) {
    if (!name) return -1;
    for (size_t i = 0; i < n; ++i)
        if (strcmp(arr[i].name, name) == 0) return (int)i;
    return -1;
}

static int nc_name_add(NcName **arr, size_t *n, size_t *cap, const char *name) {
    if (!name || !name[0]) return -1;
    int id = nc_name_find(*arr, *n, name);
    if (id >= 0) return id;
    if (*n == *cap) {
        *cap = *cap ? *cap * 2 : 8;
        *arr = (NcName *)cat_realloc(*arr, sizeof(NcName) * *cap);
    }
    (*arr)[*n].name = cat_strdup(name);
    return (int)(*n)++;
}

static void type_decl_add(NcGen *g, const char *decl);
static int  label_find(NcGen *g, const char *name);
static int  label_add(NcGen *g, const char *name);

/* ------------------------------------------------------------------ */
/* Нормализованный поиск слотов (как в интерпретаторе)                 */
/* ------------------------------------------------------------------ */

static void norm_name(const char *src, char *dst, size_t cap) {
    size_t k = 0;
    for (const char *q = src; *q && k + 1 < cap; ++q)
        if (isalnum((unsigned char)*q)) dst[k++] = (char)tolower((unsigned char)*q);
    dst[k] = 0;
}

static CatFormula *fml(CatBrick *b, ...) {
    va_list ap;
    va_start(ap, b);
    const char *want;
    while ((want = va_arg(ap, const char *)) != NULL) {
        for (size_t i = 0; i < b->slot_count; ++i) {
            if (strcmp(b->slots[i].name, want) == 0) {
                va_end(ap);
                return b->slots[i].value;
            }
        }
    }
    va_end(ap);
    /* нормализованное сравнение */
    va_start(ap, b);
    while ((want = va_arg(ap, const char *)) != NULL) {
        for (size_t i = 0; i < b->slot_count; ++i) {
            char a[64], c[64];
            norm_name(b->slots[i].name, a, sizeof a);
            norm_name(want, c, sizeof c);
            if (strcmp(a, c) == 0) {
                va_end(ap);
                return b->slots[i].value;
            }
        }
    }
    va_end(ap);
    return NULL;
}

/* ------------------------------------------------------------------ */
/* Сбор переменных и списков                                           */
/* ------------------------------------------------------------------ */

static void collect_formula(NcGen *g, CatFormula *f);

static void collect_bricks(NcGen *g, CatBrick **bricks, size_t n) {
    for (size_t i = 0; i < n; ++i) {
        CatBrick *b = bricks[i];
        if (!b) continue;
        switch (b->kind) {
        case CB_SET_VARIABLE: case CB_CHANGE_VARIABLE:
        case CB_MALLOC: case CB_CALLOC: case CB_REALLOC:
        case CB_CAST: case CB_POINTER_GET:
        case CB_TERNARY: case CB_INC: case CB_DEC: case CB_SIZEOF:
            if (b->arg0) nc_name_add(&g->vars, &g->var_count, &g->var_cap, b->arg0);
            break;
        case CB_FOR_FROM_TO:
            if (b->arg0) nc_name_add(&g->vars, &g->var_count, &g->var_cap, b->arg0);
            break;
        case CB_ADD_TO_LIST: case CB_DELETE_FROM_LIST:
        case CB_CLEAR_LIST: case CB_INSERT_INTO_LIST: case CB_REPLACE_IN_LIST:
            if (b->arg0) nc_name_add(&g->lists, &g->list_count, &g->list_cap, b->arg0);
            break;
        case CB_STRUCT: {
            char *alias = NULL, *fields = NULL;
            CatFormula *af = fml(b, "cname", "C_NAME", "name", NULL);
            CatFormula *ff = fml(b, "cfields", "C_FIELDS", "fields", "value", NULL);
            if (af && af->kind == CF_STRING) alias = cat_value_to_cstring(&af->literal);
            if (ff && ff->kind == CF_STRING) fields = cat_value_to_cstring(&ff->literal);
            if (!alias && b->arg0) alias = cat_strdup(b->arg0);
            if (!fields && b->arg1) fields = cat_strdup(b->arg1);
            if (alias && fields && fields[0]) {
                char *decl = (char *)cat_malloc(strlen(alias) + strlen(fields) + 40);
                sprintf(decl, "typedef struct { %s } %s;", fields, alias);
                type_decl_add(g, decl);
                cat_free(decl);
            }
            cat_free(alias); cat_free(fields);
            break;
        }
        case CB_ENUM: {
            char *alias = NULL, *enums = NULL;
            CatFormula *af = fml(b, "cname", "C_NAME", "name", NULL);
            CatFormula *ef = fml(b, "cenums", "C_ENUMERATORS", "enumerators", "value", NULL);
            if (af && af->kind == CF_STRING) alias = cat_value_to_cstring(&af->literal);
            if (ef && ef->kind == CF_STRING) enums = cat_value_to_cstring(&ef->literal);
            if (!alias && b->arg0) alias = cat_strdup(b->arg0);
            if (!enums && b->arg1) enums = cat_strdup(b->arg1);
            if (alias && enums && enums[0]) {
                char *decl = (char *)cat_malloc(strlen(alias) + strlen(enums) + 40);
                sprintf(decl, "typedef enum { %s } %s;", enums, alias);
                type_decl_add(g, decl);
                cat_free(decl);
            }
            cat_free(alias); cat_free(enums);
            break;
        }
        case CB_LABEL: {
            char *nm = NULL;
            CatFormula *lf = fml(b, "label", "C_LABEL", "name", NULL);
            if (lf && lf->kind == CF_STRING) nm = cat_value_to_cstring(&lf->literal);
            if (!nm && b->arg0) nm = cat_strdup(b->arg0);
            if (nm && nm[0]) label_add(g, nm);
            cat_free(nm);
            break;
        }
        default: break;
        }
        for (size_t s = 0; s < b->slot_count; ++s)
            collect_formula(g, b->slots[s].value);
        collect_bricks(g, b->children, b->child_count);
        collect_bricks(g, b->else_children, b->else_child_count);
    }
}

static void collect_formula(NcGen *g, CatFormula *f) {
    if (!f) return;
    if (f->kind == CF_VARIABLE && f->literal.type == CAT_VAL_STRING)
        nc_name_add(&g->vars, &g->var_count, &g->var_cap, f->literal.as.string);
    if (f->kind == CF_LIST && f->literal.type == CAT_VAL_STRING)
        nc_name_add(&g->lists, &g->list_count, &g->list_cap, f->literal.as.string);
    for (size_t i = 0; i < f->argc; ++i) collect_formula(g, f->args[i]);
}

/* ------------------------------------------------------------------ */
/* Генерация формул в C-выражения типа NcVal                           */
/* ------------------------------------------------------------------ */

static const char *binop_fn(const char *op) {
    if (!op) return "nc_add";
    if (strcmp(op, "+") == 0 || strcasecmp(op, "PLUS") == 0) return "nc_add";
    if (strcmp(op, "-") == 0 || strcasecmp(op, "MINUS") == 0) return "nc_sub";
    if (strcmp(op, "*") == 0 || strcasecmp(op, "MULT") == 0) return "nc_mul";
    if (strcmp(op, "/") == 0 || strcasecmp(op, "DIVIDE") == 0) return "nc_div";
    if (strcmp(op, "%") == 0 || strcasecmp(op, "MOD") == 0 || strcasecmp(op, "MODULO") == 0) return "nc_mod";
    if (strcmp(op, "^") == 0 || strcasecmp(op, "POW") == 0) return "nc_pow";
    if (strcmp(op, "<") == 0 || strcasecmp(op, "SMALLER_THAN") == 0) return "nc_lt";
    if (strcmp(op, ">") == 0 || strcasecmp(op, "GREATER_THAN") == 0) return "nc_gt";
    if (strcmp(op, "=") == 0 || strcmp(op, "==") == 0 || strcasecmp(op, "EQUAL") == 0) return "nc_eq";
    if (strcmp(op, "!=") == 0 || strcasecmp(op, "NOT_EQUAL") == 0) return "nc_ne";
    if (strcmp(op, "<=") == 0 || strcasecmp(op, "SMALLER_OR_EQUAL") == 0) return "nc_le";
    if (strcmp(op, ">=") == 0 || strcasecmp(op, "GREATER_OR_EQUAL") == 0) return "nc_ge";
    if (strcasecmp(op, "AND") == 0 || strcasecmp(op, "LOGICAL_AND") == 0) return "nc_and";
    if (strcasecmp(op, "OR") == 0 || strcasecmp(op, "LOGICAL_OR") == 0) return "nc_or";
    /* побитовые операции языка C */
    if (strcmp(op, "&") == 0 || strcasecmp(op, "BIT_AND") == 0) return "nc_bit_and";
    if (strcmp(op, "|") == 0 || strcasecmp(op, "BIT_OR") == 0) return "nc_bit_or";
    if (strcmp(op, "^") == 0 || strcasecmp(op, "BIT_XOR") == 0) return "nc_bit_xor";
    if (strcmp(op, "<<") == 0 || strcasecmp(op, "SHIFT_LEFT") == 0) return "nc_shl";
    if (strcmp(op, ">>") == 0 || strcasecmp(op, "SHIFT_RIGHT") == 0) return "nc_shr";
    return "nc_add";
}

static const char *func_fn(const char *fn) {
    if (!fn) return NULL;
    if (strcasecmp(fn, "SIN") == 0) return "nc_sin";
    if (strcasecmp(fn, "COS") == 0) return "nc_cos";
    if (strcasecmp(fn, "TAN") == 0) return "nc_tan";
    if (strcasecmp(fn, "SQRT") == 0) return "nc_sqrt";
    if (strcasecmp(fn, "ABS") == 0) return "nc_abs";
    if (strcasecmp(fn, "ROUND") == 0) return "nc_round";
    if (strcasecmp(fn, "FLOOR") == 0) return "nc_floor";
    if (strcasecmp(fn, "CEIL") == 0) return "nc_ceil";
    if (strcasecmp(fn, "LN") == 0) return "nc_ln";
    if (strcasecmp(fn, "LOG") == 0) return "nc_log";
    if (strcasecmp(fn, "EXP") == 0) return "nc_exp";
    if (strcasecmp(fn, "MIN") == 0) return "nc_min";
    if (strcasecmp(fn, "MAX") == 0) return "nc_max";
    if (strcasecmp(fn, "RAND") == 0 || strcasecmp(fn, "RANDOM") == 0) return "nc_rand";
    if (strcasecmp(fn, "LENGTH") == 0) return "nc_length";
    if (strcasecmp(fn, "JOIN") == 0) return "nc_join";
    if (strcasecmp(fn, "LETTER") == 0) return "nc_letter";
    return NULL;
}

static void emit_string_literal(SB *g, const char *s) {
    sb_puts(g, "nc_str(\"");
    for (const char *p = s ? s : ""; *p; ++p) {
        switch (*p) {
        case '"': sb_puts(g, "\\\""); break;
        case '\\': sb_puts(g, "\\\\"); break;
        case '\n': sb_puts(g, "\\n"); break;
        case '\r': sb_puts(g, "\\r"); break;
        case '\t': sb_puts(g, "\\t"); break;
        default:
            if ((unsigned char)*p < 32) sb_printf(g, "\\x%02x", (unsigned char)*p);
            else sb_putc(g, *p);
        }
    }
    sb_puts(g, "\")");
}

/* Свёртка констант: выражения из числовых литералов вычисляются на этапе
   компиляции в единственный nc_num(...). Это уменьшает сгенерированный код
   и убирает лишние вызовы nc_*-функций в рантайме (настоящая оптимизация). */
static int f_is_num(const CatFormula *f) {
    return f && f->kind == CF_NUMBER;
}

static int fold_const(SB *o, CatFormula *f) {
    double x;
    switch (f->kind) {
    case CF_UNARY_OP:
        if (!f_is_num(f->argc ? f->args[0] : NULL)) return 0;
        x = cat_value_to_number(&f->args[0]->literal);
        if (strcmp(f->op ? f->op : "-", "-") == 0 || strcasecmp(f->op, "MINUS") == 0)
            sb_printf(o, "nc_num(%.17g)", -x);
        else if (strcmp(f->op, "~") == 0 || strcasecmp(f->op, "BIT_NOT") == 0)
            sb_printf(o, "nc_num(%.17g)", (double)(~(long long)x));
        else
            sb_printf(o, "nc_bool(%d)", x == 0.0 ? 0 : 1);
        return 1;
    case CF_BINARY_OP: {
        CatFormula *a = f->argc > 0 ? f->args[0] : NULL;
        CatFormula *b = f->argc > 1 ? f->args[1] : NULL;
        if (!f_is_num(a) || !f_is_num(b)) return 0;
        double p = cat_value_to_number(&a->literal);
        double q = cat_value_to_number(&b->literal);
        const char *op = f->op ? f->op : "+";
        int is_cmp = 0, cmp = 0;
        double z = 0;
        if (strcmp(op, "+") == 0 || strcasecmp(op, "PLUS") == 0) z = p + q;
        else if (strcmp(op, "-") == 0 || strcasecmp(op, "MINUS") == 0) z = p - q;
        else if (strcmp(op, "*") == 0 || strcasecmp(op, "MULT") == 0) z = p * q;
        else if (strcmp(op, "/") == 0 || strcasecmp(op, "DIVIDE") == 0) z = q == 0 ? 0 : p / q;
        else if (strcmp(op, "%") == 0 || strcasecmp(op, "MOD") == 0 || strcasecmp(op, "MODULO") == 0) z = q == 0 ? 0 : fmod(p, q);
        else if (strcmp(op, "^") == 0 || strcasecmp(op, "POW") == 0) z = pow(p, q);
        else if (strcmp(op, "&") == 0 || strcasecmp(op, "BIT_AND") == 0) z = (double)((long long)p & (long long)q);
        else if (strcmp(op, "|") == 0 || strcasecmp(op, "BIT_OR") == 0) z = (double)((long long)p | (long long)q);
        else if (strcmp(op, "^") == 0 || strcasecmp(op, "BIT_XOR") == 0) z = (double)((long long)p ^ (long long)q);
        else if (strcmp(op, "<<") == 0 || strcasecmp(op, "SHIFT_LEFT") == 0) z = (double)((long long)p << ((int)q & 63));
        else if (strcmp(op, ">>") == 0 || strcasecmp(op, "SHIFT_RIGHT") == 0) z = (double)((long long)p >> ((int)q & 63));
        else if (strcasecmp(op, "AND") == 0 || strcasecmp(op, "LOGICAL_AND") == 0) { is_cmp = 1; cmp = (p != 0) && (q != 0); }
        else if (strcasecmp(op, "OR") == 0 || strcasecmp(op, "LOGICAL_OR") == 0) { is_cmp = 1; cmp = (p != 0) || (q != 0); }
        else if (strcmp(op, "<") == 0 || strcasecmp(op, "SMALLER_THAN") == 0) { is_cmp = 1; cmp = p < q; }
        else if (strcmp(op, ">") == 0 || strcasecmp(op, "GREATER_THAN") == 0) { is_cmp = 1; cmp = p > q; }
        else if (strcmp(op, "<=") == 0 || strcasecmp(op, "SMALLER_OR_EQUAL") == 0) { is_cmp = 1; cmp = p <= q; }
        else if (strcmp(op, ">=") == 0 || strcasecmp(op, "GREATER_OR_EQUAL") == 0) { is_cmp = 1; cmp = p >= q; }
        else if (strcmp(op, "=") == 0 || strcmp(op, "==") == 0 || strcasecmp(op, "EQUAL") == 0) { is_cmp = 1; cmp = p == q; }
        else if (strcmp(op, "!=") == 0 || strcasecmp(op, "NOT_EQUAL") == 0) { is_cmp = 1; cmp = p != q; }
        else return 0;
        if (is_cmp) sb_printf(o, "nc_bool(%d)", cmp ? 1 : 0);
        else sb_printf(o, "nc_num(%.17g)", z);
        return 1;
    }
    case CF_FUNCTION: {
        /* Сворачиваем чистые математические функции с числовыми аргументами. */
        const char *fn = f->op ? f->op : "";
        CatFormula *a = f->argc > 0 ? f->args[0] : NULL;
        CatFormula *b = f->argc > 1 ? f->args[1] : NULL;
        if (!f_is_num(a) || (f->argc > 1 && !f_is_num(b))) return 0;
        double p = cat_value_to_number(&a->literal);
        double q = b ? cat_value_to_number(&b->literal) : 0;
        if (strcasecmp(fn, "SQRT") == 0) sb_printf(o, "nc_num(%.17g)", sqrt(p));
        else if (strcasecmp(fn, "ABS") == 0) sb_printf(o, "nc_num(%.17g)", fabs(p));
        else if (strcasecmp(fn, "SIN") == 0) sb_printf(o, "nc_num(%.17g)", sin(p * M_PI / 180.0));
        else if (strcasecmp(fn, "COS") == 0) sb_printf(o, "nc_num(%.17g)", cos(p * M_PI / 180.0));
        else if (strcasecmp(fn, "TAN") == 0) sb_printf(o, "nc_num(%.17g)", tan(p * M_PI / 180.0));
        else if (strcasecmp(fn, "ROUND") == 0) sb_printf(o, "nc_num(%.17g)", floor(p + 0.5));
        else if (strcasecmp(fn, "FLOOR") == 0) sb_printf(o, "nc_num(%.17g)", floor(p));
        else if (strcasecmp(fn, "CEIL") == 0) sb_printf(o, "nc_num(%.17g)", ceil(p));
        else if (strcasecmp(fn, "LN") == 0) sb_printf(o, "nc_num(%.17g)", log(p));
        else if (strcasecmp(fn, "LOG") == 0) sb_printf(o, "nc_num(%.17g)", log10(p));
        else if (strcasecmp(fn, "EXP") == 0) sb_printf(o, "nc_num(%.17g)", exp(p));
        else if (strcasecmp(fn, "MIN") == 0) sb_printf(o, "nc_num(%.17g)", p < q ? p : q);
        else if (strcasecmp(fn, "MAX") == 0) sb_printf(o, "nc_num(%.17g)", p > q ? p : q);
        else return 0;
        return 1;
    }
    default:
        return 0;
    }
}

static void gen_c_expr(NcGen *g, CatFormula *f, const char *def);

/* Вывод double-выражения: если формула — числовой литерал, печатаем само
   число (без nc_d(nc_num(...))), иначе nc_d(...). */
static void gen_double_expr(NcGen *g, CatFormula *f, const char *def) {
    if (f_is_num(f)) {
        sb_printf(&g->out, "%.17g", cat_value_to_number(&f->literal));
    } else {
        sb_puts(&g->out, "nc_d(");
        gen_c_expr(g, f, def);
        sb_putc(&g->out, ')');
    }
}

static void gen_formula(NcGen *g, CatFormula *f) {
    if (!f) { sb_puts(&g->out, "nc_num(0)"); return; }
    SB *o = &g->out;
    switch (f->kind) {
    case CF_NUMBER:
        sb_printf(o, "nc_num(%.17g)", cat_value_to_number(&f->literal));
        break;
    case CF_STRING:
        emit_string_literal(o, f->literal.type == CAT_VAL_STRING ? f->literal.as.string : "");
        break;
    case CF_BOOL:
        sb_puts(o, cat_value_to_bool(&f->literal) ? "nc_bool(1)" : "nc_bool(0)");
        break;
    case CF_VARIABLE: {
        char *n = cat_value_to_cstring(&f->literal);
        int id = nc_name_find(g->vars, g->var_count, n);
        cat_free(n);
        if (id >= 0) sb_printf(o, "v%d", id);
        else sb_puts(o, "nc_num(0)");
        break;
    }
    case CF_LIST: {
        char *n = cat_value_to_cstring(&f->literal);
        int id = nc_name_find(g->lists, g->list_count, n);
        cat_free(n);
        if (id >= 0) sb_printf(o, "nc_list_last(&l%d)", id);
        else sb_puts(o, "nc_str(\"\")");
        break;
    }
    case CF_SENSOR: {
        char *n = cat_value_to_cstring(&f->literal);
        const char *field = NULL;
        if (strcasecmp(n, "OBJECT_X") == 0 || strcasecmp(n, "X_POSITION") == 0) field = "x";
        else if (strcasecmp(n, "OBJECT_Y") == 0 || strcasecmp(n, "Y_POSITION") == 0) field = "y";
        else if (strcasecmp(n, "OBJECT_ROTATION") == 0 || strcasecmp(n, "DIRECTION") == 0) field = "direction";
        else if (strcasecmp(n, "OBJECT_SIZE") == 0 || strcasecmp(n, "SIZE") == 0) field = "size";
        else if (strcasecmp(n, "OBJECT_TRANSPARENCY") == 0) field = "transparency";
        else if (strcasecmp(n, "OBJECT_BRIGHTNESS") == 0) field = "brightness";
        if (field) sb_printf(o, "nc_num(SP->%s)", field);
        else if (strcasecmp(n, "PI") == 0) sb_puts(o, "nc_num(3.14159265358979323846)");
        else if (strcasecmp(n, "TRUE") == 0) sb_puts(o, "nc_bool(1)");
        else if (strcasecmp(n, "FALSE") == 0) sb_puts(o, "nc_bool(0)");
        else sb_puts(o, "nc_num(0)");
        cat_free(n);
        break;
    }
    case CF_UNARY_OP: {
        if (fold_const(o, f)) break;
        const char *op = f->op ? f->op : "-";
        const char *fn;
        if (strcmp(op, "-") == 0 || strcasecmp(op, "MINUS") == 0) fn = "nc_neg";
        else if (strcmp(op, "~") == 0 || strcasecmp(op, "BIT_NOT") == 0) fn = "nc_bit_not";
        else fn = "nc_not";
        sb_printf(o, "%s(", fn);
        gen_formula(g, f->argc ? f->args[0] : NULL);
        sb_putc(o, ')');
        break;
    }
    case CF_BINARY_OP: {
        if (fold_const(o, f)) break;
        sb_printf(o, "%s(", binop_fn(f->op));
        gen_formula(g, f->argc > 0 ? f->args[0] : NULL);
        sb_puts(o, ", ");
        gen_formula(g, f->argc > 1 ? f->args[1] : NULL);
        sb_putc(o, ')');
        break;
    }
    case CF_FUNCTION: {
        if (fold_const(o, f)) break;
        const char *fn = func_fn(f->op);
        if (!fn) { sb_puts(o, "nc_num(0)"); break; }
        sb_printf(o, "%s(", fn);
        for (size_t i = 0; i < f->argc; ++i) {
            if (i) sb_puts(o, ", ");
            gen_formula(g, f->args[i]);
        }
        sb_putc(o, ')');
        break;
    }
    }
}

/* ------------------------------------------------------------------ */
/* typedef-и и типы                                                    */
/* ------------------------------------------------------------------ */

/* Регистрирует объявление составного типа (struct/enum) для вывода в шапке. */
static void type_decl_add(NcGen *g, const char *decl) {
    if (!decl || !decl[0]) return;
    if (g->type_decl_count == g->type_decl_cap) {
        g->type_decl_cap = g->type_decl_cap ? g->type_decl_cap * 2 : 8;
        g->type_decls = (char **)cat_realloc(g->type_decls, sizeof(char *) * g->type_decl_cap);
    }
    g->type_decls[g->type_decl_count++] = cat_strdup(decl);
}

static int label_find(NcGen *g, const char *name) {
    if (!name) return -1;
    for (size_t i = 0; i < g->label_count; ++i)
        if (strcmp(g->labels[i], name) == 0) return (int)i;
    return -1;
}

static int label_add(NcGen *g, const char *name) {
    if (!name || !name[0]) return -1;
    int id = label_find(g, name);
    if (id >= 0) return id;
    if (g->label_count == g->label_cap) {
        g->label_cap = g->label_cap ? g->label_cap * 2 : 8;
        g->labels = (char **)cat_realloc(g->labels, sizeof(char *) * g->label_cap);
    }
    g->labels[g->label_count] = cat_strdup(name);
    return (int)g->label_count++;
}

static void td_add(NcGen *g, const char *alias, const char *base) {
    if (!alias || !alias[0] || !base || !base[0]) return;
    for (size_t i = 0; i < g->td_count; ++i) {
        if (strcasecmp(g->tds[i].alias, alias) == 0) {
            cat_free(g->tds[i].base);
            g->tds[i].base = cat_strdup(base);
            return;
        }
    }
    if (g->td_count == g->td_cap) {
        g->td_cap = g->td_cap ? g->td_cap * 2 : 8;
        g->tds = (NcTypedef *)cat_realloc(g->tds, sizeof(NcTypedef) * g->td_cap);
    }
    g->tds[g->td_count].alias = cat_strdup(alias);
    g->tds[g->td_count].base = cat_strdup(base);
    g->td_count++;
}

static const char *resolve_type(NcGen *g, const char *type) {
    if (!type || !type[0]) return "double";
    for (int hop = 0; hop < 8; ++hop) {
        const char *found = NULL;
        for (size_t i = 0; i < g->td_count; ++i)
            if (strcasecmp(g->tds[i].alias, type) == 0) { found = g->tds[i].base; break; }
        if (!found) break;
        type = found;
    }
    return type;
}

/* Суффикс функций nc_pset/nc_pget/nc_cast для типа. */
static const char *type_suffix(const char *type) {
    if (strcasecmp(type, "double") == 0) return "d";
    if (strcasecmp(type, "float") == 0) return "f";
    if (strcasecmp(type, "int") == 0 || strcasecmp(type, "int32") == 0 ||
        strcasecmp(type, "uint32") == 0) return "i";
    if (strcasecmp(type, "long") == 0 || strcasecmp(type, "int64") == 0 ||
        strcasecmp(type, "uint64") == 0) return "l";
    if (strcasecmp(type, "short") == 0 || strcasecmp(type, "int16") == 0 ||
        strcasecmp(type, "uint16") == 0) return "s";
    if (strcasecmp(type, "char") == 0 || strcasecmp(type, "char*") == 0) return "c";
    if (strcasecmp(type, "byte") == 0 || strcasecmp(type, "int8") == 0 ||
        strcasecmp(type, "uint8") == 0) return "b";
    if (strcasecmp(type, "bool") == 0 || strcasecmp(type, "boolean") == 0 ||
        strcasecmp(type, "_bool") == 0) return "bool";
    return "d";
}

/* ------------------------------------------------------------------ */
/* Генерация брикков                                                   */
/* ------------------------------------------------------------------ */

static void gen_bricks(NcGen *g, CatBrick **bricks, size_t n);

static void gen_c_expr(NcGen *g, CatFormula *f, const char *def) {
    if (f) gen_formula(g, f);
    else sb_puts(&g->out, def);
}

static void gen_bricks(NcGen *g, CatBrick **bricks, size_t n) {
    SB *o = &g->out;
    for (size_t i = 0; i < n; ++i) {
        CatBrick *b = bricks[i];
        if (!b) continue;
        CatSprite *sp = g->sprite;
        const char *nm = sp ? sp->name : "Object";
        switch (b->kind) {
        /* --- переменные --- */
        case CB_SET_VARIABLE: {
            sb_indent(o);
            int id = b->arg0 ? nc_name_find(g->vars, g->var_count, b->arg0) : -1;
            if (id >= 0) {
                sb_printf(o, "v%d = ", id);
                gen_c_expr(g, fml(b, "value", "VARIABLE", NULL), "nc_num(0)");
                sb_puts(o, ";\n");
            } else {
                sb_puts(o, "; /* set: неизвестная переменная */\n");
            }
            break;
        }
        case CB_CHANGE_VARIABLE: {
            sb_indent(o);
            int id = b->arg0 ? nc_name_find(g->vars, g->var_count, b->arg0) : -1;
            if (id >= 0) {
                sb_printf(o, "v%d = nc_add(v%d, ", id, id);
                gen_c_expr(g, fml(b, "value", "VARIABLE_CHANGE", NULL), "nc_num(0)");
                sb_puts(o, ");\n");
            } else {
                sb_puts(o, "; /* change: неизвестная переменная */\n");
            }
            break;
        }
        /* --- списки --- */
        case CB_ADD_TO_LIST: {
            int id = b->arg0 ? nc_name_find(g->lists, g->list_count, b->arg0) : -1;
            if (id >= 0) {
                sb_indent(o);
                sb_printf(o, "nc_list_add(&l%d, ", id);
                gen_c_expr(g, fml(b, "value", "LIST_ADD_ITEM", NULL), "nc_num(0)");
                sb_puts(o, ");\n");
            }
            break;
        }
        case CB_DELETE_FROM_LIST: {
            int id = b->arg0 ? nc_name_find(g->lists, g->list_count, b->arg0) : -1;
            if (id >= 0) {
                sb_indent(o);
                sb_printf(o, "nc_list_delete(&l%d, ", id);
                gen_c_expr(g, fml(b, "value", "LIST_DELETE_ITEM", NULL), "nc_num(1)");
                sb_puts(o, ");\n");
            }
            break;
        }
        case CB_CLEAR_LIST:
            if (b->arg0) {
                int id = nc_name_find(g->lists, g->list_count, b->arg0);
                if (id >= 0) sb_line(o, "l%d.count = 0;", id);
            }
            break;
        case CB_INSERT_INTO_LIST: {
            int id = b->arg0 ? nc_name_find(g->lists, g->list_count, b->arg0) : -1;
            if (id >= 0) {
                sb_indent(o);
                sb_printf(o, "nc_list_insert(&l%d, ", id);
                gen_c_expr(g, fml(b, "index", "INSERT_ITEM_INTO_USERLIST_INDEX", NULL), "nc_num(1)");
                sb_puts(o, ", ");
                gen_c_expr(g, fml(b, "value", "INSERT_ITEM_INTO_USERLIST_VALUE", NULL), "nc_num(0)");
                sb_puts(o, ");\n");
            }
            break;
        }
        case CB_REPLACE_IN_LIST: {
            int id = b->arg0 ? nc_name_find(g->lists, g->list_count, b->arg0) : -1;
            if (id >= 0) {
                sb_indent(o);
                sb_printf(o, "nc_list_replace(&l%d, ", id);
                gen_c_expr(g, fml(b, "index", "REPLACE_ITEM_IN_USERLIST_INDEX", NULL), "nc_num(1)");
                sb_puts(o, ", ");
                gen_c_expr(g, fml(b, "value", "REPLACE_ITEM_IN_USERLIST_VALUE", NULL), "nc_num(0)");
                sb_puts(o, ");\n");
            }
            break;
        }
        /* --- движение ---
         * ВАЖНО: поля спрайта (SP->x, SP->y, ...) имеют тип double, а формулы
         * генерируют значения типа NcVal (структура). Поэтому любое присваивание
         * поля спрайта обязано оборачивать формулу в nc_d(...), иначе С-компилятор
         * выдаёт «incompatible types when assigning to type 'double' from type 'NcVal'». */
        case CB_PLACE_AT:
            sb_indent(o); sb_puts(o, "SP->x = "); gen_double_expr(g, fml(b, "x", "X_POSITION", NULL), "nc_num(0)"); sb_puts(o, ";\n");
            sb_indent(o); sb_puts(o, "SP->y = "); gen_double_expr(g, fml(b, "y", "Y_POSITION", NULL), "nc_num(0)"); sb_puts(o, ";\n");
            break;
        case CB_SET_X:
            sb_indent(o); sb_puts(o, "SP->x = "); gen_double_expr(g, fml(b, "x", "X_POSITION", NULL), "nc_num(0)"); sb_puts(o, ";\n");
            break;
        case CB_SET_Y:
            sb_indent(o); sb_puts(o, "SP->y = "); gen_double_expr(g, fml(b, "y", "Y_POSITION", NULL), "nc_num(0)"); sb_puts(o, ";\n");
            break;
        case CB_CHANGE_X:
            /* Напрямую += (как в cat_interpreter.c: sp->x += to_number(v)),
               без лишнего NcVal-раунда и без строковой конкатенации. */
            sb_indent(o);
            sb_puts(o, "SP->x += ");
            gen_double_expr(g, fml(b, "x", "X_POSITION_CHANGE", NULL), "nc_num(0)");
            sb_puts(o, ";\n");
            break;
        case CB_CHANGE_Y:
            sb_indent(o);
            sb_puts(o, "SP->y += ");
            gen_double_expr(g, fml(b, "y", "Y_POSITION_CHANGE", NULL), "nc_num(0)");
            sb_puts(o, ";\n");
            break;
        case CB_MOVE_STEPS: {
            int t = ++g->out.counter;
            sb_line(o, "{");
            g->out.indent++;
            sb_indent(o); sb_printf(o, "double rad_%d = (90.0 - SP->direction) * 3.14159265358979323846 / 180.0;\n", t);
            sb_indent(o); sb_printf(o, "double s_%d = nc_d(", t);
            gen_c_expr(g, fml(b, "steps", "STEPS", NULL), "nc_num(0)");
            sb_puts(o, ");\n");
            sb_line(o, "SP->x += s_%d * cos(rad_%d);", t, t);
            sb_line(o, "SP->y += s_%d * sin(rad_%d);", t, t);
            g->out.indent--;
            sb_line(o, "}");
            break;
        }
        case CB_TURN_LEFT:
            sb_indent(o); sb_puts(o, "SP->direction -= ");
            gen_double_expr(g, fml(b, "degrees", "TURN_LEFT_DEGREES", "DEGREES", NULL), "nc_num(0)");
            sb_puts(o, ";\n");
            break;
        case CB_TURN_RIGHT:
            sb_indent(o); sb_puts(o, "SP->direction += ");
            gen_double_expr(g, fml(b, "degrees", "TURN_RIGHT_DEGREES", "DEGREES", NULL), "nc_num(0)");
            sb_puts(o, ";\n");
            break;
        case CB_POINT_IN_DIRECTION:
            sb_indent(o); sb_puts(o, "SP->direction = ");
            gen_double_expr(g, fml(b, "degrees", "DEGREES", NULL), "nc_num(90)");
            sb_puts(o, ";\n");
            break;
        case CB_GLIDE_TO:
            sb_indent(o); sb_puts(o, "nc_glide(&SP->x, &SP->y, nc_d(");
            gen_c_expr(g, fml(b, "x", "X_DESTINATION", NULL), "nc_num(0)");
            sb_puts(o, "), nc_d(");
            gen_c_expr(g, fml(b, "y", "Y_DESTINATION", NULL), "nc_num(0)");
            sb_puts(o, "), nc_d(");
            gen_c_expr(g, fml(b, "seconds", "DURATION_IN_SECONDS", NULL), "nc_num(0)");
            sb_puts(o, "));\n");
            break;
        /* --- внешний вид --- */
        case CB_SHOW: sb_line(o, "SP->visible = 1;"); break;
        case CB_HIDE: sb_line(o, "SP->visible = 0;"); break;
        case CB_SET_SIZE_TO:
            sb_indent(o); sb_puts(o, "SP->size = ");
            gen_double_expr(g, fml(b, "size", "SIZE", NULL), "nc_num(100)");
            sb_puts(o, ");\n");
            break;
        case CB_CHANGE_SIZE_BY:
            sb_indent(o); sb_puts(o, "SP->size += ");
            gen_double_expr(g, fml(b, "size", "SIZE_CHANGE", NULL), "nc_num(0)");
            sb_puts(o, ");\n");
            break;
        case CB_SAY:
            sb_indent(o); sb_printf(o, "nc_say(\"%s\", ", nm);
            gen_c_expr(g, fml(b, "text", "STRING", "value", "SAY", NULL), "nc_str(\"\")");
            sb_puts(o, ");\n");
            break;
        case CB_THINK:
            sb_indent(o); sb_printf(o, "nc_think(\"%s\", ", nm);
            gen_c_expr(g, fml(b, "text", "STRING", "value", NULL), "nc_str(\"\")");
            sb_puts(o, ");\n");
            break;
        case CB_SAY_FOR:
            sb_indent(o); sb_printf(o, "nc_say(\"%s\", ", nm);
            gen_c_expr(g, fml(b, "text", "STRING", "value", NULL), "nc_str(\"\")");
            sb_puts(o, "); nc_wait(nc_d(");
            gen_c_expr(g, fml(b, "seconds", "DURATION_IN_SECONDS", NULL), "nc_num(0)");
            sb_puts(o, "));\n");
            break;
        case CB_THINK_FOR:
            sb_indent(o); sb_printf(o, "nc_think(\"%s\", ", nm);
            gen_c_expr(g, fml(b, "text", "STRING", "value", NULL), "nc_str(\"\")");
            sb_puts(o, "); nc_wait(nc_d(");
            gen_c_expr(g, fml(b, "seconds", "DURATION_IN_SECONDS", NULL), "nc_num(0)");
            sb_puts(o, "));\n");
            break;
        case CB_SET_LOOK: case CB_NEXT_LOOK: case CB_PREVIOUS_LOOK:
            sb_line(o, "; /* look: %s */", b->arg0 ? b->arg0 : "");
            break;
        /* --- звук --- */
        case CB_PLAY_SOUND:
            sb_indent(o); sb_printf(o, "nc_sound(\"%s\");\n", b->arg0 ? b->arg0 : "");
            break;
        case CB_STOP_ALL_SOUNDS:
            sb_line(o, "; /* stop all sounds */");
            break;
        case CB_SET_VOLUME: case CB_CHANGE_VOLUME:
            sb_line(o, "; /* volume */");
            break;
        /* --- управление --- */
        case CB_WAIT:
            sb_indent(o); sb_puts(o, "nc_wait(");
            gen_double_expr(g, fml(b, "seconds", "DURATION_IN_SECONDS", "TIME_TO_WAIT_IN_SECONDS", NULL), "nc_num(0)");
            sb_puts(o, ");\n");
            break;
        case CB_BROADCAST: case CB_BROADCAST_WAIT: {
            const char *msg = b->arg0 ? b->arg0 : "";
            int id = -1;
            for (size_t k = 0; k < g->bmsg_count; ++k)
                if (strcmp(g->bmsg[k], msg) == 0) { id = (int)k; break; }
            if (id >= 0) sb_line(o, "bc%d(); /* broadcast \"%s\" */", id, msg);
            else sb_line(o, "; /* broadcast \"%s\": получателей нет */", msg);
            break;
        }
        case CB_FOREVER:
            sb_line(o, "for (;;) {");
            g->out.indent++; g->loop_depth++;
            gen_bricks(g, b->children, b->child_count);
            sb_line(o, "nc_tick();");
            g->out.indent--; g->loop_depth--;
            sb_line(o, "}");
            break;
        case CB_REPEAT: {
            int t = ++g->out.counter;
            sb_indent(o); sb_printf(o, "for (int i_%d = 0; i_%d < (int)nc_d(", t, t);
            gen_c_expr(g, fml(b, "times", "TIMES_TO_REPEAT", NULL), "nc_num(0)");
            sb_printf(o, "); ++i_%d) {\n", t);
            g->out.indent++; g->loop_depth++;
            gen_bricks(g, b->children, b->child_count);
            g->out.indent--; g->loop_depth--;
            sb_line(o, "}");
            break;
        }
        case CB_REPEAT_UNTIL:
            sb_indent(o); sb_puts(o, "while (!nc_truthy(");
            gen_c_expr(g, fml(b, "condition", "REPEAT_UNTIL_CONDITION", NULL), "nc_bool(0)");
            sb_puts(o, ")) {\n");
            g->out.indent++; g->loop_depth++;
            gen_bricks(g, b->children, b->child_count);
            g->out.indent--; g->loop_depth--;
            sb_line(o, "}");
            break;
        case CB_IF_BEGIN: case CB_IF_THEN_BEGIN:
            sb_indent(o); sb_puts(o, "if (nc_truthy(");
            gen_c_expr(g, fml(b, "condition", "IF_CONDITION", NULL), "nc_bool(0)");
            sb_puts(o, ")) {\n");
            g->out.indent++;
            gen_bricks(g, b->children, b->child_count);
            g->out.indent--;
            if (b->else_child_count) {
                sb_line(o, "} else {");
                g->out.indent++;
                gen_bricks(g, b->else_children, b->else_child_count);
                g->out.indent--;
            }
            sb_line(o, "}");
            break;
        case CB_STOP_SCRIPT: sb_line(o, "return;"); break;
        case CB_STOP_ALL: sb_line(o, "exit(0);"); break;
        case CB_STOP_OTHER: sb_line(o, "; /* stop other scripts */"); break;
        case CB_NOTE: sb_line(o, "; /* note: %s */", b->arg0 ? b->arg0 : ""); break;
        case CB_PRINT:
            sb_indent(o); sb_puts(o, "nc_print(");
            gen_c_expr(g, fml(b, "value", "VARIABLE", NULL), "nc_num(0)");
            sb_puts(o, ");\n");
            break;
        /* --- служебные концы блоков --- */
        case CB_LOOP_END: case CB_IF_ELSE: case CB_IF_END: case CB_IF_THEN_END:
            break;
        /* --- НИЗКОУРОВНЕВЫЕ C-БЛОКИ: настоящий C, машинный код --- */
        case CB_MALLOC:
            sb_indent(o);
            if (b->arg0 && nc_name_find(g->vars, g->var_count, b->arg0) >= 0) {
                sb_printf(o, "v%d = nc_malloc((size_t)nc_d(", nc_name_find(g->vars, g->var_count, b->arg0));
                gen_c_expr(g, fml(b, "csize", "C_SIZE", "size", NULL), "nc_num(0)");
                sb_puts(o, "));\n");
            } else {
                sb_puts(o, "nc_free(nc_num((double)(uintptr_t)malloc((size_t)nc_d(");
                gen_c_expr(g, fml(b, "csize", "C_SIZE", "size", NULL), "nc_num(0)");
                sb_puts(o, ")))); /* результат отброшен */\n");
            }
            break;
        case CB_CALLOC: {
            int id = b->arg0 ? nc_name_find(g->vars, g->var_count, b->arg0) : -1;
            sb_indent(o);
            if (id >= 0) sb_printf(o, "v%d = nc_calloc((size_t)nc_d(", id);
            else sb_puts(o, "nc_free(nc_calloc((size_t)nc_d(");
            gen_c_expr(g, fml(b, "ccount", "C_COUNT", "count", NULL), "nc_num(0)");
            sb_puts(o, "), (size_t)nc_d(");
            gen_c_expr(g, fml(b, "csize", "C_SIZE", "size", NULL), "nc_num(0)");
            if (id >= 0) sb_puts(o, "));\n");
            else sb_puts(o, "))); /* результат отброшен */\n");
            break;
        }
        case CB_REALLOC: {
            int id = b->arg0 ? nc_name_find(g->vars, g->var_count, b->arg0) : -1;
            sb_indent(o);
            if (id >= 0) sb_printf(o, "v%d = nc_realloc(", id);
            else sb_puts(o, "nc_realloc(");
            gen_c_expr(g, fml(b, "cpointer", "C_POINTER", "pointer", NULL), "nc_num(0)");
            sb_puts(o, ", (size_t)nc_d(");
            gen_c_expr(g, fml(b, "csize", "C_SIZE", "size", NULL), "nc_num(0)");
            sb_puts(o, "));\n");
            break;
        }
        case CB_FREE:
            sb_indent(o); sb_puts(o, "nc_free(");
            gen_c_expr(g, fml(b, "cpointer", "C_POINTER", "pointer", NULL), "nc_num(0)");
            sb_puts(o, ");\n");
            break;
        case CB_MEMCPY:
            sb_indent(o); sb_puts(o, "nc_memcpy(");
            gen_c_expr(g, fml(b, "cdestination", "C_DESTINATION", "destination", "dest", NULL), "nc_num(0)");
            sb_puts(o, ", ");
            gen_c_expr(g, fml(b, "csource", "C_SOURCE", "source", "src", NULL), "nc_num(0)");
            sb_puts(o, ", (size_t)nc_d(");
            gen_c_expr(g, fml(b, "csize", "C_SIZE", "size", NULL), "nc_num(0)");
            sb_puts(o, "));\n");
            break;
        case CB_MEMSET:
            sb_indent(o); sb_puts(o, "nc_memset(");
            gen_c_expr(g, fml(b, "cpointer", "C_POINTER", "pointer", NULL), "nc_num(0)");
            sb_puts(o, ", (int)nc_d(");
            gen_c_expr(g, fml(b, "cvalue", "C_VALUE", "value", NULL), "nc_num(0)");
            sb_puts(o, "), (size_t)nc_d(");
            gen_c_expr(g, fml(b, "csize", "C_SIZE", "size", NULL), "nc_num(0)");
            sb_puts(o, "));\n");
            break;
        case CB_TYPEDEF: {
            char *alias = NULL, *base = NULL;
            CatFormula *af = fml(b, "cname", "C_NAME", "name", NULL);
            CatFormula *bf = fml(b, "cbasetype", "C_BASE_TYPE", "basetype", "base", NULL);
            if (af && af->kind == CF_STRING) alias = cat_value_to_cstring(&af->literal);
            if (bf && bf->kind == CF_STRING) base = cat_value_to_cstring(&bf->literal);
            if (!alias && b->arg0) alias = cat_strdup(b->arg0);
            if (!base && b->arg1) base = cat_strdup(b->arg1);
            if (alias && base) td_add(g, alias, base);
            sb_line(o, "; /* typedef %s = %s */", alias ? alias : "?", base ? base : "?");
            cat_free(alias);
            cat_free(base);
            break;
        }
        case CB_CAST: {
            char *type = NULL;
            CatFormula *tf = fml(b, "ctype", "C_TYPE", "type", NULL);
            if (tf && tf->kind == CF_STRING) type = cat_value_to_cstring(&tf->literal);
            if (!type && b->arg1) type = cat_strdup(b->arg1);
            if (!type) type = cat_strdup("double");
            sb_indent(o);
            int id = b->arg0 ? nc_name_find(g->vars, g->var_count, b->arg0) : -1;
            if (id >= 0) {
                sb_printf(o, "v%d = nc_cast_%s(", id, type_suffix(resolve_type(g, type)));
                gen_c_expr(g, fml(b, "cvalue", "C_VALUE", "value", NULL), "nc_num(0)");
                sb_puts(o, ");\n");
            } else {
                sb_puts(o, "; /* cast: нет переменной */\n");
            }
            cat_free(type);
            break;
        }
        case CB_POINTER_SET: {
            char *type = NULL;
            CatFormula *tf = fml(b, "ctype", "C_TYPE", "type", NULL);
            if (tf && tf->kind == CF_STRING) type = cat_value_to_cstring(&tf->literal);
            if (!type) type = cat_strdup("double");
            sb_indent(o);
            sb_printf(o, "nc_pset_%s(", type_suffix(resolve_type(g, type)));
            gen_c_expr(g, fml(b, "cpointer", "C_POINTER", "pointer", NULL), "nc_num(0)");
            sb_puts(o, ", ");
            gen_c_expr(g, fml(b, "coffset", "C_OFFSET", "offset", NULL), "nc_num(0)");
            sb_puts(o, ", ");
            gen_c_expr(g, fml(b, "cvalue", "C_VALUE", "value", NULL), "nc_num(0)");
            sb_puts(o, ");\n");
            cat_free(type);
            break;
        }
        case CB_POINTER_GET: {
            char *type = NULL;
            CatFormula *tf = fml(b, "ctype", "C_TYPE", "type", NULL);
            if (tf && tf->kind == CF_STRING) type = cat_value_to_cstring(&tf->literal);
            if (!type) type = cat_strdup("double");
            sb_indent(o);
            int id = b->arg0 ? nc_name_find(g->vars, g->var_count, b->arg0) : -1;
            if (id >= 0) {
                sb_printf(o, "v%d = nc_pget_%s(", id, type_suffix(resolve_type(g, type)));
                gen_c_expr(g, fml(b, "cpointer", "C_POINTER", "pointer", NULL), "nc_num(0)");
                sb_puts(o, ", ");
                gen_c_expr(g, fml(b, "coffset", "C_OFFSET", "offset", NULL), "nc_num(0)");
                sb_puts(o, ");\n");
            } else {
                sb_puts(o, "; /* pointer get: нет переменной */\n");
            }
            cat_free(type);
            break;
        }
        case CB_RETURN: sb_line(o, "return;"); break;
        case CB_BREAK:
            if (g->loop_depth > 0) sb_line(o, "break;");
            else sb_line(o, "; /* break вне цикла */");
            break;
        case CB_CONTINUE:
            if (g->loop_depth > 0) sb_line(o, "continue;");
            else sb_line(o, "; /* continue вне цикла */");
            break;
        /* --- Выполнение произвольного кода (free-text inline) --- */
        case CB_C_CODE: {
            /* Сырой C из строкового слота встраивается прямо в вывод —
               выполняется как нативный машинный код вместе с программой. */
            CatFormula *cf = fml(b, "code", "ccode", "c_code", "value", NULL);
            char *owned = (cf && cf->kind == CF_STRING) ? cat_value_to_cstring(&cf->literal) : NULL;
            const char *raw = owned ? owned : (b->arg0 ? b->arg0 : "");
            sb_line(o, "{ /* выполнить код C */");
            g->out.indent++;
            if (raw && raw[0]) {
                for (const char *p = raw; *p; ) {
                    const char *nl = strchr(p, '\n');
                    size_t len = nl ? (size_t)(nl - p) : strlen(p);
                    sb_indent(o);
                    sb_printf(o, "%.*s\n", (int)len, p);
                    if (!nl) break;
                    p = nl + 1;
                }
            } else {
                sb_line(o, "; /* пустой блок кода C */");
            }
            g->out.indent--;
            sb_line(o, "}");
            cat_free(owned);
            break;
        }
        case CB_JAVA_CODE: {
            /* В нативной C-сборке JVM нет — исходник Java сохраняется как
               документирующий комментарий. Исполняется скриптовым движком
               в Android-интерпретаторе. */
            CatFormula *cf = fml(b, "code", "jcode", "java_code", "value", NULL);
            char *owned = (cf && cf->kind == CF_STRING) ? cat_value_to_cstring(&cf->literal) : NULL;
            const char *raw = owned ? owned : (b->arg0 ? b->arg0 : "");
            sb_line(o, "/* выполнить код Java: недоступно в нативной C-компиляции */");
            if (raw && raw[0]) {
                for (const char *p = raw; *p; ) {
                    const char *nl = strchr(p, '\n');
                    size_t len = nl ? (size_t)(nl - p) : strlen(p);
                    sb_indent(o);
                    sb_printf(o, "// %.*s\n", (int)len, p);
                    if (!nl) break;
                    p = nl + 1;
                }
            }
            cat_free(owned);
            break;
        }
        /* --- Клоны спрайтов: пул поз + общие скрипты (см. шаг 4b) --- */
        case CB_CLONE:
            if (g->sprite_i >= 0 && (size_t)g->sprite_i < g->clone_scripts_n &&
                g->clone_scripts[g->sprite_i] > 0) {
                sb_line(o, "clone_fire_%d(SP); /* clone: копия только позы */", g->sprite_i);
            } else {
                sb_line(o, "; /* clone: у спрайта нет скриптов WhenCloned */");
            }
            break;
        case CB_DELETE_THIS_CLONE:
            /* В статике клон живёт внутри clone_fire: return завершает
               текущий скрипт, после остальных слот пула освобождается. */
            sb_line(o, "return; /* delete this clone */");
            break;
        /* --- Расширенный набор языка C --- */
        case CB_WHILE:
            sb_indent(o); sb_puts(o, "while (nc_truthy(");
            gen_c_expr(g, fml(b, "IF_CONDITION", "condition", "WHILE_CONDITION", NULL), "nc_bool(0)");
            sb_puts(o, ")) {\n");
            g->out.indent++; g->loop_depth++;
            gen_bricks(g, b->children, b->child_count);
            g->out.indent--; g->loop_depth--;
            sb_line(o, "}");
            break;
        case CB_DO_WHILE:
            sb_line(o, "do {");
            g->out.indent++; g->loop_depth++;
            gen_bricks(g, b->children, b->child_count);
            g->out.indent--; g->loop_depth--;
            sb_indent(o); sb_puts(o, "} while (nc_truthy(");
            gen_c_expr(g, fml(b, "IF_CONDITION", "condition", "DO_WHILE_CONDITION", NULL), "nc_bool(0)");
            sb_puts(o, "));\n");
            break;
        case CB_FOR_FROM_TO: {
            int id = b->arg0 ? nc_name_find(g->vars, g->var_count, b->arg0) : -1;
            int t = ++g->out.counter;
            sb_line(o, "{");
            g->out.indent++;
            sb_indent(o); sb_printf(o, "double f_%d = nc_d(", t);
            gen_c_expr(g, fml(b, "from", "FROM", NULL), "nc_num(0)");
            sb_puts(o, ");\n");
            sb_indent(o); sb_printf(o, "double t_%d = nc_d(", t);
            gen_c_expr(g, fml(b, "to", "TO", NULL), "nc_num(0)");
            sb_puts(o, ");\n");
            sb_indent(o); sb_printf(o, "double s_%d = nc_d(", t);
            gen_c_expr(g, fml(b, "step", "STEP", "increment", "INCREMENT", "value", NULL), "nc_num(1)");
            sb_puts(o, ");\n");
            sb_indent(o); sb_printf(o, "long n_%d = (s_%d == 0.0) ? 0 : (long)((t_%d - f_%d) / s_%d);\n", t, t, t, t, t);
            sb_indent(o); sb_printf(o, "if (n_%d < 0) n_%d = 0; else n_%d += 1;\n", t, t, t);
            sb_indent(o); sb_printf(o, "for (long i_%d = 0; i_%d < n_%d; ++i_%d) {\n", t, t, t, t);
            g->out.indent++; g->loop_depth++;
            if (id >= 0) sb_line(o, "v%d = nc_num(f_%d + (double)i_%d * s_%d);", id, t, t, t);
            else sb_line(o, "; /* for: неизвестная переменная */");
            gen_bricks(g, b->children, b->child_count);
            g->out.indent--; g->loop_depth--;
            sb_line(o, "}");
            g->out.indent--;
            sb_line(o, "}");
            break;
        }
        case CB_SWITCH:
            sb_indent(o); sb_puts(o, "switch ((long long)nc_d(");
            gen_c_expr(g, fml(b, "value", "SWITCH_VALUE", NULL), "nc_num(0)");
            sb_puts(o, ")) {\n");
            g->out.indent++;
            gen_bricks(g, b->children, b->child_count);
            g->out.indent--;
            sb_line(o, "}");
            break;
        case CB_CASE: {
            /* case-метки в C обязаны быть целочисленными константами времени
               компиляции, поэтому динамические значения превращаем в
               комментарий-предупреждение (реальный C так и работает). */
            CatFormula *vf = fml(b, "value", "CASE_VALUE", NULL);
            if (f_is_num(vf)) {
                sb_indent(o);
                sb_printf(o, "case (long long)%.17g:\n", cat_value_to_number(&vf->literal));
            } else {
                sb_line(o, "; /* case: значение не константа — пропущено */");
            }
            break;
        }
        case CB_CASE_BREAK:
            sb_line(o, "break;");
            break;
        case CB_SWITCH_END:
            break;
        case CB_GOTO: {
            char *nm = NULL;
            CatFormula *gf = fml(b, "label", "C_LABEL", "name", NULL);
            if (gf && gf->kind == CF_STRING) nm = cat_value_to_cstring(&gf->literal);
            if (!nm && b->arg0) nm = cat_strdup(b->arg0);
            int id = label_find(g, nm ? nm : "");
            if (id >= 0) sb_line(o, "goto nc_lbl_%d; /* goto %s */", id, nm ? nm : "?");
            else sb_line(o, "; /* goto %s: метки нет */", nm ? nm : "?");
            cat_free(nm);
            break;
        }
        case CB_LABEL: {
            char *nm = NULL;
            CatFormula *lf = fml(b, "label", "C_LABEL", "name", NULL);
            if (lf && lf->kind == CF_STRING) nm = cat_value_to_cstring(&lf->literal);
            if (!nm && b->arg0) nm = cat_strdup(b->arg0);
            int id = label_find(g, nm ? nm : "");
            if (id >= 0) sb_line(o, "nc_lbl_%d: ; /* label %s */", id, nm ? nm : "?");
            else sb_line(o, "; /* label %s: неизвестно */", nm ? nm : "?");
            cat_free(nm);
            break;
        }
        case CB_TERNARY: {
            int id = b->arg0 ? nc_name_find(g->vars, g->var_count, b->arg0) : -1;
            sb_indent(o);
            if (id >= 0) sb_printf(o, "v%d = (nc_truthy(", id);
            else sb_puts(o, "; (");
            gen_c_expr(g, fml(b, "TERNARY_CONDITION", "condition", NULL), "nc_bool(0)");
            sb_puts(o, ") ? ");
            gen_c_expr(g, fml(b, "TERNARY_IF_TRUE", "ciftrue", "IF_TRUE", "iftrue", NULL), "nc_num(0)");
            sb_puts(o, " : ");
            gen_c_expr(g, fml(b, "TERNARY_IF_FALSE", "ciffalse", "IF_FALSE", "iffalse", NULL), "nc_num(0)");
            if (id >= 0) sb_puts(o, ");\n");
            else sb_puts(o, "); /* ternary: нет переменной */\n");
            break;
        }
        case CB_INC: {
            int id = b->arg0 ? nc_name_find(g->vars, g->var_count, b->arg0) : -1;
            if (id >= 0) sb_line(o, "v%d = nc_add(v%d, nc_num(1)); /* v%d++ */", id, id, id);
            else sb_line(o, "; /* var++: неизвестная переменная */");
            break;
        }
        case CB_DEC: {
            int id = b->arg0 ? nc_name_find(g->vars, g->var_count, b->arg0) : -1;
            if (id >= 0) sb_line(o, "v%d = nc_sub(v%d, nc_num(1)); /* v%d-- */", id, id, id);
            else sb_line(o, "; /* var--: неизвестная переменная */");
            break;
        }
        case CB_SIZEOF: {
            char *type = NULL;
            CatFormula *tf = fml(b, "ctype", "C_TYPE", "type", NULL);
            if (tf && tf->kind == CF_STRING) type = cat_value_to_cstring(&tf->literal);
            if (!type && b->arg1) type = cat_strdup(b->arg1);
            if (!type) type = cat_strdup("double");
            int id = b->arg0 ? nc_name_find(g->vars, g->var_count, b->arg0) : -1;
            sb_indent(o);
            if (id >= 0)
                sb_printf(o, "v%d = nc_num((double)sizeof(%s)); /* sizeof(%s) */\n",
                          id, resolve_type(g, type), type);
            else
                sb_printf(o, "; /* sizeof(%s) */\n", type);
            cat_free(type);
            break;
        }
        case CB_STRUCT: case CB_ENUM:
            /* Объявление вынесено в шапку (collect_bricks); здесь — комментарий. */
            sb_line(o, "; /* %s: объявлено в шапке программы */",
                    b->kind == CB_STRUCT ? "struct" : "enum");
            break;
        case CB_ASSERT:
            sb_indent(o); sb_puts(o, "assert(nc_truthy(");
            gen_c_expr(g, fml(b, "IF_CONDITION", "condition", "ASSERT_CONDITION", "value", NULL), "nc_bool(0)");
            sb_puts(o, "));\n");
            break;
        default:
            sb_line(o, "; /* brick #%d пропущен */", (int)b->kind);
            break;
        }
    }
}

/* ------------------------------------------------------------------ */
/* Генерация всего проекта                                             */
/* ------------------------------------------------------------------ */

static void gen_script_decl(NcGen *g, int si, int ki) {
    sb_printf(&g->out, "s%d_%d", si, ki);
}

char *cat_compile_to_c(CatProject *p) {
    if (!p) return NULL;
    NcGen g;
    memset(&g, 0, sizeof g);
    g.p = p;
    g.out.cap = 65536;
    g.out.buf = (char *)cat_calloc(1, g.out.cap);

    /* 0. Посчитать спрайты и их скрипты «когда я начинаю как клон». */
    {
        size_t total = 0;
        for (size_t si = 0; si < p->scene_count; ++si)
            total += p->scenes[si]->sprite_count;
        g.clone_scripts_n = total;
        g.clone_scripts = (int *)cat_calloc(total ? total : 1, sizeof(int));
        size_t idx = 0;
        for (size_t si = 0; si < p->scene_count; ++si) {
            CatScene *sc = p->scenes[si];
            for (size_t spi = 0; spi < sc->sprite_count; ++spi, ++idx) {
                CatSprite *sp = sc->sprites[spi];
                for (size_t k = 0; k < sp->script_count; ++k)
                    if (sp->scripts[k]->head && sp->scripts[k]->head->kind == CB_WHEN_CLONED)
                        g.clone_scripts[idx]++;
            }
        }
    }

    /* 1. Собрать переменные/списки по всем спрайтам. */
    for (size_t si = 0; si < p->scene_count; ++si) {
        CatScene *sc = p->scenes[si];
        for (size_t spi = 0; spi < sc->sprite_count; ++spi) {
            CatSprite *sp = sc->sprites[spi];
            for (size_t k = 0; k < sp->script_count; ++k) {
                CatScript *scr = sp->scripts[k];
                if (scr->head && scr->head->kind == CB_WHEN_BROADCAST && scr->head->arg0) {
                    /* сообщение */
                    int found = -1;
                    for (size_t m = 0; m < g.bmsg_count; ++m)
                        if (strcmp(g.bmsg[m], scr->head->arg0) == 0) { found = (int)m; break; }
                    if (found < 0) {
                        if (g.bmsg_count == g.bmsg_cap) {
                            g.bmsg_cap = g.bmsg_cap ? g.bmsg_cap * 2 : 8;
                            g.bmsg = (char **)cat_realloc(g.bmsg, sizeof(char *) * g.bmsg_cap);
                        }
                        g.bmsg[g.bmsg_count++] = cat_strdup(scr->head->arg0);
                    }
                }
                collect_bricks(&g, scr->bricks, scr->brick_count);
            }
        }
    }

    SB *o = &g.out;
    sb_line(o, "/*");
    sb_line(o, " * Сгенерировано компилятором NewCode из проекта \"%s\".", p->name ? p->name : "");
    sb_line(o, " * Это настоящая C-программа: она компилируется в машинный код.");
    sb_line(o, " * Никакого байт-кода и никакого GC: значения на стеке,");
    sb_line(o, " * память C-блоков управляется явно (malloc/free).");
    sb_line(o, " */");
    sb_line(o, "#include \"nc_rt.h\"");
    sb_line(o, "");
    /* Объявления составных типов (struct/enum) из блоков. */
    for (size_t i = 0; i < g.type_decl_count; ++i)
        sb_line(o, "%s", g.type_decls[i]);
    if (g.type_decl_count) sb_line(o, "");
    sb_line(o, "/* Клоны: фиксированный пул поз на спрайт. Копируется только поза;\n"
              "   куча и число клонов не раздуваются => проект не лагает.\n"
              "   Свободный слот ищется за O(1) по битовой маске (__builtin_ctzll). */");
    sb_line(o, "#ifndef NC_CLONE_CAP");
    sb_line(o, "#define NC_CLONE_CAP 64 /* максимум 64: битовая маска 64 бита */");
    sb_line(o, "#endif");
    sb_line(o, "");

    /* 2. Состояние спрайтов. */
    sb_line(o, "typedef struct {");
    sb_line(o, "    double x, y, direction, size, transparency, brightness;");
    sb_line(o, "    int visible;");
    sb_line(o, "} Spr;");
    for (size_t si = 0, idx = 0; si < p->scene_count; ++si) {
        CatScene *sc = p->scenes[si];
        for (size_t spi = 0; spi < sc->sprite_count; ++spi, ++idx) {
            CatSprite *sp = sc->sprites[spi];
            sb_line(o, "static Spr spr%zu; /* спрайт: %s */", idx, sp->name ? sp->name : "?");
            if (g.clone_scripts[idx] > 0) {
                sb_line(o, "static Spr nc_clones_%zu[NC_CLONE_CAP]; /* пул клонов: %s */", idx, sp->name ? sp->name : "?");
                sb_line(o, "static unsigned long long nc_clone_used_%zu; /* битовая маска занятых слотов */", idx);
            }
        }
    }
    sb_line(o, "");

    /* 3. Переменные и списки. */
    for (size_t i = 0; i < g.var_count; ++i)
        sb_line(o, "static NcVal v%zu; /* переменная: %s */", i, g.vars[i].name);
    for (size_t i = 0; i < g.list_count; ++i)
        sb_line(o, "static NcList l%zu; /* список: %s */", i, g.lists[i].name);
    sb_line(o, "");

    /* 4a. Предварительные объявления: скрипты, broadcast, клоны. */
    for (size_t si = 0, idx = 0; si < p->scene_count; ++si) {
        CatScene *sc = p->scenes[si];
        for (size_t spi = 0; spi < sc->sprite_count; ++spi, ++idx) {
            CatSprite *sp = sc->sprites[spi];
            for (size_t k = 0; k < sp->script_count; ++k) {
                if (!sp->scripts[k] || !sp->scripts[k]->head) continue;
                sb_printf(o, "static void ");
                gen_script_decl(&g, (int)idx, (int)k);
                sb_printf(o, "(Spr *SP);\n");
            }
            if (g.clone_scripts[idx] > 0)
                sb_line(o, "static void clone_fire_%zu(Spr *src);", idx);
        }
    }
    for (size_t m = 0; m < g.bmsg_count; ++m)
        sb_line(o, "static void bc%zu(void);", m);
    sb_line(o, "");

    /* 4. Функции скриптов. */
    for (size_t si = 0, idx = 0; si < p->scene_count; ++si) {
        CatScene *sc = p->scenes[si];
        for (size_t spi = 0; spi < sc->sprite_count; ++spi, ++idx) {
            CatSprite *sp = sc->sprites[spi];
            g.sprite = sp;
            g.sprite_i = (int)idx;
            for (size_t k = 0; k < sp->script_count; ++k) {
                CatScript *scr = sp->scripts[k];
                if (!scr || !scr->head) continue;
                const char *kind = "скрипт";
                if (scr->head->kind == CB_WHEN_STARTED) kind = "WhenStarted";
                else if (scr->head->kind == CB_WHEN_BROADCAST) kind = "WhenBroadcast";
                else if (scr->head->kind == CB_WHEN_TAPPED) kind = "WhenTapped";
                else if (scr->head->kind == CB_WHEN_CLONED) kind = "WhenCloned";
                sb_printf(o, "static void ");
                gen_script_decl(&g, (int)idx, (int)k);
                sb_printf(o, "(Spr *SP) { /* %s: %s */\n", kind, sp->name ? sp->name : "?");
                g.out.indent = 1;
                g.loop_depth = 0;
                gen_bricks(&g, scr->bricks, scr->brick_count);
                g.out.indent = 0;
                sb_line(o, "}");
                sb_line(o, "");
            }
        }
    }

    /* 4b. Клоны: синхронный запуск скриптов WhenCloned из пула поз.
       Поза — побитовая копия структуры Spr (O(1)); скрипты и переменные
       общие. Статический рантайм последовательный, поэтому клон «живёт»
       внутри clone_fire: отработал -> слот пула снова свободен. */
    for (size_t si = 0, idx = 0; si < p->scene_count; ++si) {
        CatScene *sc = p->scenes[si];
        for (size_t spi = 0; spi < sc->sprite_count; ++spi, ++idx) {
            if (g.clone_scripts[idx] == 0) continue;
            CatSprite *sp = sc->sprites[spi];
            sb_printf(o, "static void clone_fire_%zu(Spr *src) { /* клон спрайта \"%s\" */\n",
                      idx, sp->name ? sp->name : "?");
            sb_line(o, "    unsigned long long free_mask_%zu = ~nc_clone_used_%zu;", idx, idx);
            sb_line(o, "    if (free_mask_%zu == 0ULL) return; /* пул клонов исчерпан */", idx);
            sb_line(o, "    int ci = __builtin_ctzll(free_mask_%zu); /* первый свободный слот, O(1) */", idx);
            sb_line(o, "    nc_clone_used_%zu |= (1ULL << ci);", idx);
            sb_line(o, "    nc_clones_%zu[ci] = *src; /* поза — побитовая копия, ноль strdup/GC */", idx);
            for (size_t k = 0; k < sp->script_count; ++k) {
                CatScript *scr = sp->scripts[k];
                if (scr->head && scr->head->kind == CB_WHEN_CLONED) {
                    sb_printf(o, "    ");
                    gen_script_decl(&g, (int)idx, (int)k);
                    sb_printf(o, "(&nc_clones_%zu[ci]);\n", idx);
                }
            }
            sb_line(o, "    nc_clone_used_%zu &= ~(1ULL << ci); /* delete this clone / конец скриптов */", idx);
            sb_line(o, "}");
            sb_line(o, "");
        }
    }

    /* 5. Диспетчеры broadcast-сообщений. */
    for (size_t m = 0; m < g.bmsg_count; ++m) {
        sb_printf(o, "static void bc%zu(void) { /* broadcast \"%s\" */\n", m, g.bmsg[m]);
        for (size_t si = 0, idx = 0; si < p->scene_count; ++si) {
            CatScene *sc = p->scenes[si];
            for (size_t spi = 0; spi < sc->sprite_count; ++spi, ++idx) {
                CatSprite *sp = sc->sprites[spi];
                for (size_t k = 0; k < sp->script_count; ++k) {
                    CatScript *scr = sp->scripts[k];
                    if (scr->head && scr->head->kind == CB_WHEN_BROADCAST && scr->head->arg0 &&
                        strcmp(scr->head->arg0, g.bmsg[m]) == 0) {
                        sb_printf(o, "    ");
                        gen_script_decl(&g, (int)idx, (int)k);
                        sb_printf(o, "(&spr%zu);\n", idx);
                    }
                }
            }
        }
        sb_line(o, "}");
        sb_line(o, "");
    }

    /* 6. main(). */
    sb_line(o, "int main(void) {");
    for (size_t si = 0, idx = 0; si < p->scene_count; ++si) {
        CatScene *sc = p->scenes[si];
        for (size_t spi = 0; spi < sc->sprite_count; ++spi, ++idx) {
            sb_line(o, "    spr%zu.direction = 90.0; spr%zu.size = 100.0; spr%zu.visible = 1;", idx, idx, idx);
        }
    }
    sb_line(o, "    nc_seed();");
    for (size_t si = 0, idx = 0; si < p->scene_count; ++si) {
        CatScene *sc = p->scenes[si];
        for (size_t spi = 0; spi < sc->sprite_count; ++spi, ++idx) {
            CatSprite *sp = sc->sprites[spi];
            for (size_t k = 0; k < sp->script_count; ++k) {
                CatScript *scr = sp->scripts[k];
                if (scr->head && scr->head->kind == CB_WHEN_STARTED) {
                    sb_printf(o, "    ");
                    gen_script_decl(&g, (int)idx, (int)k);
                    sb_printf(o, "(&spr%zu);\n", idx);
                }
            }
        }
    }
    sb_line(o, "    return 0;");
    sb_line(o, "}");

    /* 7. Очистка таблиц. */
    for (size_t i = 0; i < g.var_count; ++i) cat_free(g.vars[i].name);
    cat_free(g.vars);
    for (size_t i = 0; i < g.list_count; ++i) cat_free(g.lists[i].name);
    cat_free(g.lists);
    for (size_t i = 0; i < g.td_count; ++i) { cat_free(g.tds[i].alias); cat_free(g.tds[i].base); }
    cat_free(g.tds);
    for (size_t i = 0; i < g.type_decl_count; ++i) cat_free(g.type_decls[i]);
    cat_free(g.type_decls);
    for (size_t i = 0; i < g.label_count; ++i) cat_free(g.labels[i]);
    cat_free(g.labels);
    for (size_t i = 0; i < g.bmsg_count; ++i) cat_free(g.bmsg[i]);
    cat_free(g.bmsg);
    cat_free(g.clone_scripts);

    return g.out.buf;
}

/* Текст мини-рантайма nc_rt.h для записи рядом со сгенерированным кодом. */
const char *cat_compiler_runtime_header(void) {
    return NC_RT_DATA;
}
