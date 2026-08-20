#include "cat_interpreter.h"
#include "cat_mem.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <ctype.h>
#include <stdint.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

/* ---------- Формулы ---------- */

static int ieq(const char *a, const char *b) { return a && b && strcasecmp(a, b) == 0; }

static CatValue eval_sensor(CatSprite *sp, const char *name) {
    if (!name) return cat_value_number(0);
    if (ieq(name, "OBJECT_X") || ieq(name, "X_POSITION")) return cat_value_number(sp ? sp->x : 0);
    if (ieq(name, "OBJECT_Y") || ieq(name, "Y_POSITION")) return cat_value_number(sp ? sp->y : 0);
    if (ieq(name, "OBJECT_ROTATION") || ieq(name, "DIRECTION")) return cat_value_number(sp ? sp->direction : 90);
    if (ieq(name, "OBJECT_SIZE") || ieq(name, "SIZE")) return cat_value_number(sp ? sp->size : 100);
    if (ieq(name, "OBJECT_TRANSPARENCY")) return cat_value_number(sp ? sp->transparency : 0);
    if (ieq(name, "OBJECT_BRIGHTNESS")) return cat_value_number(sp ? sp->brightness : 100);
    if (ieq(name, "PI")) return cat_value_number(M_PI);
    if (ieq(name, "TRUE")) return cat_value_bool(true);
    if (ieq(name, "FALSE")) return cat_value_bool(false);
    return cat_value_number(0);
}

static CatValue eval_binop(const char *op, CatValue a, CatValue b) {
    CatValue r;
    if (ieq(op, "AND") || ieq(op, "LOGICAL_AND")) r = cat_value_bool(cat_value_to_bool(&a) && cat_value_to_bool(&b));
    else if (ieq(op, "OR") || ieq(op, "LOGICAL_OR")) r = cat_value_bool(cat_value_to_bool(&a) || cat_value_to_bool(&b));
    else if (ieq(op, "EQUAL") || strcmp(op, "=") == 0 || strcmp(op, "==") == 0) {
        if (a.type == CAT_VAL_STRING || b.type == CAT_VAL_STRING) {
            char *sa = cat_value_to_cstring(&a), *sb = cat_value_to_cstring(&b);
            r = cat_value_bool(strcmp(sa, sb) == 0);
            cat_free(sa); cat_free(sb);
        } else {
            r = cat_value_bool(cat_value_to_number(&a) == cat_value_to_number(&b));
        }
    }
    else if (ieq(op, "NOT_EQUAL") || strcmp(op, "!=") == 0) {
        double x = cat_value_to_number(&a), y = cat_value_to_number(&b);
        r = cat_value_bool(x != y);
    }
    else {
        double x = cat_value_to_number(&a), y = cat_value_to_number(&b), z = 0;
        if (strcmp(op, "+") == 0 || ieq(op, "PLUS")) z = x + y;
        else if (strcmp(op, "-") == 0 || ieq(op, "MINUS")) z = x - y;
        else if (strcmp(op, "*") == 0 || ieq(op, "MULT")) z = x * y;
        else if (strcmp(op, "/") == 0 || ieq(op, "DIVIDE")) z = y == 0 ? 0 : x / y;
        else if (strcmp(op, "%") == 0 || ieq(op, "MOD") || ieq(op, "MODULO")) z = y == 0 ? 0 : fmod(x, y);
        else if (strcmp(op, "^") == 0 || ieq(op, "POW")) z = pow(x, y);
        else if (strcmp(op, "<") == 0 || ieq(op, "SMALLER_THAN")) { r = cat_value_bool(x < y); goto done; }
        else if (strcmp(op, ">") == 0 || ieq(op, "GREATER_THAN")) { r = cat_value_bool(x > y); goto done; }
        else if (strcmp(op, "<=") == 0 || ieq(op, "SMALLER_OR_EQUAL")) { r = cat_value_bool(x <= y); goto done; }
        else if (strcmp(op, ">=") == 0 || ieq(op, "GREATER_OR_EQUAL")) { r = cat_value_bool(x >= y); goto done; }
        r = cat_value_number(z);
    }
done:
    cat_value_free(&a); cat_value_free(&b);
    return r;
}

static CatValue eval_unary(const char *op, CatValue a) {
    CatValue r;
    if (ieq(op, "NOT") || ieq(op, "LOGICAL_NOT")) r = cat_value_bool(!cat_value_to_bool(&a));
    else if (strcmp(op, "-") == 0 || ieq(op, "MINUS")) r = cat_value_number(-cat_value_to_number(&a));
    else r = cat_value_copy(&a);
    cat_value_free(&a);
    return r;
}

/* Прото для внешнего доступа. */
struct CatEngine;
static CatValue eval_formula_internal(struct CatEngine *e, CatSprite *sp, const CatFormula *f);

static CatValue eval_function(struct CatEngine *e, CatSprite *sp, const char *fn, CatFormula **args, size_t argc) {
    CatValue r = cat_value_number(0);
    #define ARG(i) (i < argc ? eval_formula_internal(e, sp, args[i]) : cat_value_number(0))
    if (ieq(fn, "SIN"))      { CatValue a=ARG(0); r=cat_value_number(sin(cat_value_to_number(&a)*M_PI/180.0)); cat_value_free(&a); }
    else if (ieq(fn,"COS"))  { CatValue a=ARG(0); r=cat_value_number(cos(cat_value_to_number(&a)*M_PI/180.0)); cat_value_free(&a); }
    else if (ieq(fn,"TAN"))  { CatValue a=ARG(0); r=cat_value_number(tan(cat_value_to_number(&a)*M_PI/180.0)); cat_value_free(&a); }
    else if (ieq(fn,"SQRT")) { CatValue a=ARG(0); r=cat_value_number(sqrt(cat_value_to_number(&a))); cat_value_free(&a); }
    else if (ieq(fn,"ABS"))  { CatValue a=ARG(0); r=cat_value_number(fabs(cat_value_to_number(&a))); cat_value_free(&a); }
    else if (ieq(fn,"ROUND")){ CatValue a=ARG(0); r=cat_value_number(floor(cat_value_to_number(&a)+0.5)); cat_value_free(&a); }
    else if (ieq(fn,"FLOOR")){ CatValue a=ARG(0); r=cat_value_number(floor(cat_value_to_number(&a))); cat_value_free(&a); }
    else if (ieq(fn,"CEIL")) { CatValue a=ARG(0); r=cat_value_number(ceil(cat_value_to_number(&a))); cat_value_free(&a); }
    else if (ieq(fn,"LN"))   { CatValue a=ARG(0); r=cat_value_number(log(cat_value_to_number(&a))); cat_value_free(&a); }
    else if (ieq(fn,"LOG"))  { CatValue a=ARG(0); r=cat_value_number(log10(cat_value_to_number(&a))); cat_value_free(&a); }
    else if (ieq(fn,"EXP"))  { CatValue a=ARG(0); r=cat_value_number(exp(cat_value_to_number(&a))); cat_value_free(&a); }
    else if (ieq(fn,"MIN"))  { CatValue a=ARG(0),b=ARG(1); double x=cat_value_to_number(&a),y=cat_value_to_number(&b); r=cat_value_number(x<y?x:y); cat_value_free(&a); cat_value_free(&b); }
    else if (ieq(fn,"MAX"))  { CatValue a=ARG(0),b=ARG(1); double x=cat_value_to_number(&a),y=cat_value_to_number(&b); r=cat_value_number(x>y?x:y); cat_value_free(&a); cat_value_free(&b); }
    else if (ieq(fn,"RAND") || ieq(fn,"RANDOM")) {
        CatValue a=ARG(0), b=ARG(1);
        double lo=cat_value_to_number(&a), hi=cat_value_to_number(&b);
        if (hi<lo){double t=lo;lo=hi;hi=t;}
        double u = (double)rand()/(double)RAND_MAX;
        r = cat_value_number(lo + u*(hi-lo));
        cat_value_free(&a); cat_value_free(&b);
    }
    else if (ieq(fn,"LENGTH")) {
        CatValue a=ARG(0);
        char *s = cat_value_to_cstring(&a);
        r = cat_value_number((double)strlen(s));
        cat_free(s); cat_value_free(&a);
    }
    else if (ieq(fn,"JOIN")) {
        CatValue a=ARG(0), b=ARG(1);
        char *sa=cat_value_to_cstring(&a), *sb=cat_value_to_cstring(&b);
        size_t la=strlen(sa), lb=strlen(sb);
        char *r2=(char*)cat_malloc(la+lb+1);
        memcpy(r2,sa,la); memcpy(r2+la,sb,lb+1);
        r = cat_value_string_take(r2);
        cat_free(sa); cat_free(sb); cat_value_free(&a); cat_value_free(&b);
    }
    else if (ieq(fn,"LETTER")) {
        CatValue a=ARG(0), b=ARG(1);
        long idx=(long)cat_value_to_number(&a);
        char *s=cat_value_to_cstring(&b);
        long len=(long)strlen(s);
        char buf[2]={0,0};
        if (idx>=1 && idx<=len) buf[0]=s[idx-1];
        r=cat_value_string(buf);
        cat_free(s); cat_value_free(&a); cat_value_free(&b);
    }
    #undef ARG
    return r;
}

/* ---------- C-блоки: симулируемая «куча» и указатели ---------- */
/*
 * Указатели — это обычные числовые адреса (записываются в переменные
 * Catrobat и могут использоваться в формулах). Куча — безопасная песочница:
 * реальная память процесса не затрагивается, все обращения проверяются.
 */
#define C_ADDR_BASE     4096.0             /* первый адрес */
#define C_HEAP_MAX_BYTES (16u * 1024u * 1024u) /* лимит всей кучи */
#define C_TYPEDEF_MAX_HOPS 8

typedef struct {
    unsigned char *data;
    size_t         size;
    double         addr;
    bool           used;
} CAlloc;

typedef struct {
    CAlloc *allocs;
    size_t  count;
    size_t  cap;
    double  next_addr;
    size_t  total_bytes;
} CHeap;

typedef struct {
    char *alias;
    char *base;
} CTypedef;

static void c_heap_init(CHeap *h) {
    h->allocs = NULL; h->count = 0; h->cap = 0;
    h->next_addr = C_ADDR_BASE; h->total_bytes = 0;
}

static void c_heap_clear(CHeap *h) {
    for (size_t i = 0; i < h->count; ++i) {
        cat_free(h->allocs[i].data);
        h->allocs[i].data = NULL;
        h->allocs[i].used = false;
    }
    cat_free(h->allocs);
    c_heap_init(h);
}

/* Выделить блок; возвращает адрес или 0 (NULL) при неудаче. */
static double c_heap_alloc(CHeap *h, size_t size, bool zero) {
    if (size == 0 || h->total_bytes + size > C_HEAP_MAX_BYTES) return 0;
    /* Переиспользуем слот освобождённого блока. */
    CAlloc *slot = NULL;
    for (size_t i = 0; i < h->count; ++i) {
        if (!h->allocs[i].used) { slot = &h->allocs[i]; break; }
    }
    if (!slot) {
        if (h->count == h->cap) {
            h->cap = h->cap ? h->cap * 2 : 8;
            h->allocs = (CAlloc *)cat_realloc(h->allocs, sizeof(CAlloc) * h->cap);
        }
        slot = &h->allocs[h->count++];
        slot->data = NULL; slot->size = 0; slot->addr = 0; slot->used = false;
    }
    unsigned char *data = (unsigned char *)cat_realloc(slot->data, size);
    if (!data) return 0;
    if (zero) memset(data, 0, size);
    slot->data = data;
    slot->size = size;
    slot->used = true;
    h->total_bytes += size;
    if (!slot->addr) { /* новому слоту выдаём свежий адрес */
        slot->addr = h->next_addr;
        h->next_addr += (double)((size + 15u) & ~(size_t)15u);
    }
    return slot->addr;
}

/* Найти активный блок, содержащий адрес. */
static CAlloc *c_heap_find(CHeap *h, double addr) {
    for (size_t i = 0; i < h->count; ++i) {
        CAlloc *a = &h->allocs[i];
        if (a->used && addr >= a->addr && addr < a->addr + (double)a->size)
            return a;
    }
    return NULL;
}

static void c_heap_free(CHeap *h, double addr) {
    CAlloc *a = c_heap_find(h, addr);
    if (!a) return;
    cat_free(a->data);
    a->data = NULL; a->used = false; a->size = 0; a->addr = 0;
}

/* --- typedef-реестр --- */
static void c_typedefs_clear(CTypedef *items, size_t count) {
    for (size_t i = 0; i < count; ++i) { cat_free(items[i].alias); cat_free(items[i].base); }
    cat_free(items);
}

/* Размер значения типа в байтах (как в C на типичной платформе). */
static size_t c_type_size(const char *type) {
    if (!type) return 8;
    if (strcasecmp(type, "char") == 0 || strcasecmp(type, "byte") == 0 ||
        strcasecmp(type, "bool") == 0 || strcasecmp(type, "boolean") == 0 ||
        strcasecmp(type, "_bool") == 0 || strcasecmp(type, "uint8") == 0 ||
        strcasecmp(type, "int8") == 0 || strcasecmp(type, "char*") == 0) return 1;
    if (strcasecmp(type, "short") == 0 || strcasecmp(type, "int16") == 0 ||
        strcasecmp(type, "uint16") == 0) return 2;
    if (strcasecmp(type, "int") == 0 || strcasecmp(type, "int32") == 0 ||
        strcasecmp(type, "uint32") == 0 || strcasecmp(type, "float") == 0) return 4;
    return 8; /* long, double, int64, uint64, указатели и прочее */
}

static uint64_t c_load_le(const unsigned char *p, size_t n) {
    uint64_t v = 0;
    for (size_t i = 0; i < n; ++i) v |= (uint64_t)p[i] << (8u * i);
    return v;
}

static void c_store_le(unsigned char *p, size_t n, uint64_t v) {
    for (size_t i = 0; i < n; ++i) p[i] = (unsigned char)((v >> (8u * i)) & 0xFFu);
}

/* Кодирование значения (число или строка) в байты указанного типа. */
static void c_encode(unsigned char *dst, size_t n, const char *type, const CatValue *v) {
    if (strcasecmp(type, "double") == 0) {
        double d = cat_value_to_number(v);
        uint64_t bits = 0;
        memcpy(&bits, &d, sizeof(double));
        c_store_le(dst, 8 < n ? 8 : n, bits);
    } else if (strcasecmp(type, "float") == 0) {
        float f = (float)cat_value_to_number(v);
        uint32_t bits = 0;
        memcpy(&bits, &f, sizeof(float));
        c_store_le(dst, 4 < n ? 4 : n, bits);
    } else if (strcasecmp(type, "char") == 0 || strcasecmp(type, "char*") == 0) {
        if (v->type == CAT_VAL_STRING && v->as.string && v->as.string[0]) dst[0] = (unsigned char)v->as.string[0];
        else dst[0] = (unsigned char)cat_value_to_number(v);
    } else if (strcasecmp(type, "bool") == 0 || strcasecmp(type, "boolean") == 0 ||
               strcasecmp(type, "_bool") == 0) {
        dst[0] = cat_value_to_bool(v) ? 1 : 0;
    } else { /* целые со знаком */
        long long x = (long long)cat_value_to_number(v);
        c_store_le(dst, n, (uint64_t)x);
    }
}

/* Декодирование значения из байтов по типу. */
static CatValue c_decode(const unsigned char *src, size_t n, const char *type) {
    if (strcasecmp(type, "double") == 0) {
        uint64_t bits = c_load_le(src, 8 < n ? 8 : n);
        double d = 0;
        memcpy(&d, &bits, sizeof(double));
        return cat_value_number(d);
    }
    if (strcasecmp(type, "float") == 0) {
        uint32_t bits = (uint32_t)c_load_le(src, 4 < n ? 4 : n);
        float f = 0;
        memcpy(&f, &bits, sizeof(float));
        return cat_value_number((double)f);
    }
    if (strcasecmp(type, "char") == 0 || strcasecmp(type, "char*") == 0) {
        char buf[2] = { (char)src[0], 0 };
        return cat_value_string(buf);
    }
    if (strcasecmp(type, "bool") == 0 || strcasecmp(type, "boolean") == 0 ||
        strcasecmp(type, "_bool") == 0) {
        return cat_value_bool(src[0] != 0);
    }
    /* целые со знаком со знакорасширением */
    uint64_t raw = c_load_le(src, n);
    long long x;
    if (n == 1) x = (long long)(int8_t)(uint8_t)raw;
    else if (n == 2) x = (long long)(int16_t)(uint16_t)raw;
    else if (n == 4) x = (long long)(int32_t)(uint32_t)raw;
    else x = (long long)raw;
    return cat_value_number((double)x);
}

/* Приведение значения к типу (cast): аналогично c_decode по семантике. */
static CatValue c_cast_value(const char *type, const CatValue *v) {
    if (!type) type = "double";
    if (strcasecmp(type, "char") == 0 || strcasecmp(type, "char*") == 0) {
        char buf[2] = { 0, 0 };
        if (v->type == CAT_VAL_STRING && v->as.string && v->as.string[0]) buf[0] = v->as.string[0];
        else buf[0] = (char)cat_value_to_number(v);
        return cat_value_string(buf);
    }
    if (strcasecmp(type, "bool") == 0 || strcasecmp(type, "boolean") == 0 ||
        strcasecmp(type, "_bool") == 0) {
        return cat_value_bool(cat_value_to_bool(v));
    }
    if (strcasecmp(type, "int") == 0 || strcasecmp(type, "int32") == 0 ||
        strcasecmp(type, "uint32") == 0 || strcasecmp(type, "long") == 0 ||
        strcasecmp(type, "int64") == 0 || strcasecmp(type, "uint64") == 0 ||
        strcasecmp(type, "short") == 0 || strcasecmp(type, "int16") == 0 ||
        strcasecmp(type, "uint16") == 0 || strcasecmp(type, "byte") == 0 ||
        strcasecmp(type, "int8") == 0 || strcasecmp(type, "uint8") == 0) {
        double d = cat_value_to_number(v);
        return cat_value_number(d >= 0 ? floor(d) : ceil(d));
    }
    if (strcasecmp(type, "float") == 0) {
        float f = (float)cat_value_to_number(v);
        return cat_value_number((double)f);
    }
    return cat_value_number(cat_value_to_number(v));
}

/* ---------- Планировщик ---------- */

typedef struct Frame {
    CatBrick **bricks;
    size_t     count;
    size_t     ip;
    /* для циклов */
    int        loop_kind; /* 0=none, 1=forever, 2=repeat, 3=repeat_until */
    long       repeat_left;
    CatFormula *cond;
} Frame;

typedef struct Fiber {
    CatSprite *sprite;
    CatScript *script;
    Frame     *frames;
    size_t     frame_count;
    size_t     frame_cap;
    double     sleep_left;   /* сек до пробуждения */
    bool       waiting_broadcast;
    char      *waiting_msg;
    bool       done;
} Fiber;

struct CatEngine {
    CatProject *project;
    Fiber     **fibers;
    size_t      fiber_count;
    size_t      fiber_cap;
    double      elapsed;
    /* Состояние C-блоков: куча и typedef-реестр. */
    CHeap       heap;
    CTypedef   *typedefs;
    size_t      typedef_count;
    size_t      typedef_cap;
};

static void push_frame(Fiber *f, CatBrick **bs, size_t n, int loop, long left, CatFormula *cond) {
    if (f->frame_count == f->frame_cap) {
        f->frame_cap = f->frame_cap ? f->frame_cap * 2 : 8;
        f->frames = (Frame *)cat_realloc(f->frames, sizeof(Frame) * f->frame_cap);
    }
    Frame fr = {0};
    fr.bricks = bs; fr.count = n; fr.ip = 0;
    fr.loop_kind = loop; fr.repeat_left = left; fr.cond = cond;
    f->frames[f->frame_count++] = fr;
}

static void spawn(CatEngine *e, CatSprite *sp, CatScript *sc) {
    Fiber *f = (Fiber *)cat_calloc(1, sizeof(Fiber));
    f->sprite = sp;
    f->script = sc;
    push_frame(f, sc->bricks, sc->brick_count, 0, 0, NULL);
    if (e->fiber_count == e->fiber_cap) {
        e->fiber_cap = e->fiber_cap ? e->fiber_cap * 2 : 8;
        e->fibers = (Fiber **)cat_realloc(e->fibers, sizeof(Fiber *) * e->fiber_cap);
    }
    e->fibers[e->fiber_count++] = f;
}

CatEngine *cat_engine_new(CatProject *project) {
    CatEngine *e = (CatEngine *)cat_calloc(1, sizeof(CatEngine));
    e->project = project;
    c_heap_init(&e->heap);
    return e;
}

/* Разрешение имени типа по цепочке typedef'ов. */
static const char *c_resolve_type(CatEngine *e, const char *type) {
    if (!type || !type[0]) return "double";
    for (int hop = 0; hop < C_TYPEDEF_MAX_HOPS; ++hop) {
        const char *found = NULL;
        for (size_t i = 0; i < e->typedef_count; ++i) {
            if (strcasecmp(e->typedefs[i].alias, type) == 0) {
                found = e->typedefs[i].base;
                break;
            }
        }
        if (!found) break;
        type = found;
    }
    return type;
}

/* Зарегистрировать typedef: alias -> base. */
static void c_typedef_add(CatEngine *e, const char *alias, const char *base) {
    if (!alias || !alias[0] || !base || !base[0]) return;
    for (size_t i = 0; i < e->typedef_count; ++i) {
        if (strcasecmp(e->typedefs[i].alias, alias) == 0) {
            cat_free(e->typedefs[i].base);
            e->typedefs[i].base = cat_strdup(base);
            return;
        }
    }
    if (e->typedef_count == e->typedef_cap) {
        e->typedef_cap = e->typedef_cap ? e->typedef_cap * 2 : 8;
        e->typedefs = (CTypedef *)cat_realloc(e->typedefs, sizeof(CTypedef) * e->typedef_cap);
    }
    e->typedefs[e->typedef_count].alias = cat_strdup(alias);
    e->typedefs[e->typedef_count].base = cat_strdup(base);
    e->typedef_count++;
}

static void fiber_free(Fiber *f) {
    if (!f) return;
    cat_free(f->frames);
    cat_free(f->waiting_msg);
    cat_free(f);
}

void cat_engine_free(CatEngine *e) {
    if (!e) return;
    for (size_t i = 0; i < e->fiber_count; ++i) fiber_free(e->fibers[i]);
    cat_free(e->fibers);
    c_heap_clear(&e->heap);
    c_typedefs_clear(e->typedefs, e->typedef_count);
    cat_free(e);
}

void cat_engine_start(CatEngine *e) {
    for (size_t si = 0; si < e->project->scene_count; ++si) {
        CatScene *sc = e->project->scenes[si];
        for (size_t spi = 0; spi < sc->sprite_count; ++spi) {
            CatSprite *sp = sc->sprites[spi];
            for (size_t k = 0; k < sp->script_count; ++k) {
                CatScript *scr = sp->scripts[k];
                if (scr->head && scr->head->kind == CB_WHEN_STARTED)
                    spawn(e, sp, scr);
            }
        }
    }
}

void cat_engine_broadcast(CatEngine *e, const char *msg) {
    if (!msg) return;
    for (size_t si = 0; si < e->project->scene_count; ++si) {
        CatScene *sc = e->project->scenes[si];
        for (size_t spi = 0; spi < sc->sprite_count; ++spi) {
            CatSprite *sp = sc->sprites[spi];
            for (size_t k = 0; k < sp->script_count; ++k) {
                CatScript *scr = sp->scripts[k];
                if (scr->head && scr->head->kind == CB_WHEN_BROADCAST &&
                    scr->head->arg0 && strcmp(scr->head->arg0, msg) == 0) {
                    spawn(e, sp, scr);
                }
            }
        }
    }
    /* Разбудить всех, кто ждал этого сообщения. */
    for (size_t i = 0; i < e->fiber_count; ++i) {
        Fiber *f = e->fibers[i];
        if (f->waiting_broadcast && f->waiting_msg && strcmp(f->waiting_msg, msg) == 0) {
            f->waiting_broadcast = false;
            cat_free(f->waiting_msg); f->waiting_msg = NULL;
        }
    }
}

static CatValue slot_or(CatEngine *e, CatSprite *sp, CatBrick *b, const char *name, double def) {
    CatFormula *f = cat_brick_slot(b, name);
    if (!f) return cat_value_number(def);
    return eval_formula_internal(e, sp, f);
}

/* --- Слоты C-блоков ---
 * Android пишет категории формул как имена BrickField ("C_SIZE"), ручной XML —
 * как простые слова ("size"). Ищем без учёта регистра/подчёркиваний. */
static CatFormula *c_slot(CatBrick *b, const char *const *names, size_t n) {
    for (size_t i = 0; i < n; ++i) {
        CatFormula *f = cat_brick_slot(b, names[i]);
        if (f) return f;
    }
    for (size_t i = 0; i < b->slot_count; ++i) {
        const char *p = b->slots[i].name;
        size_t k = 0;
        for (const char *q = p; *q; ++q)
            if (isalnum((unsigned char)*q)) ++k;
        char *norm = (char *)cat_malloc(k + 1);
        k = 0;
        for (const char *q = p; *q; ++q)
            if (isalnum((unsigned char)*q)) norm[k++] = (char)tolower((unsigned char)*q);
        norm[k] = 0;
        CatFormula *found = NULL;
        for (size_t j = 0; j < n; ++j) {
            if (strcmp(norm, names[j]) == 0) { found = b->slots[i].value; break; }
        }
        cat_free(norm);
        if (found) return found;
    }
    return NULL;
}

/* Вычислить слот C-блока как число. */
static double c_slot_num(CatEngine *e, CatSprite *sp, CatBrick *b,
                         const char *const *names, size_t n, double def) {
    CatFormula *f = c_slot(b, names, n);
    if (!f) return def;
    CatValue v = eval_formula_internal(e, sp, f);
    double d = cat_value_to_number(&v);
    cat_value_free(&v);
    return d;
}

/* Вычислить слот C-блока как строку (тип, имя typedef'а). */
static char *c_slot_str(CatEngine *e, CatSprite *sp, CatBrick *b,
                        const char *const *names, size_t n) {
    CatFormula *f = c_slot(b, names, n);
    if (!f) return NULL;
    CatValue v = eval_formula_internal(e, sp, f);
    char *s = cat_value_to_cstring(&v);
    cat_value_free(&v);
    return s;
}


/* Выполняет один брикк из текущего кадра. Возвращает: 0 продолжить,
   1 приостановить (yield). */
static int exec_brick(CatEngine *e, Fiber *fi, CatBrick *b) {
    CatSprite *sp = fi->sprite;
    switch (b->kind) {
    case CB_WAIT: {
        CatValue v = slot_or(e, sp, b, "seconds", 0);
        fi->sleep_left = cat_value_to_number(&v);
        cat_value_free(&v);
        return 1;
    }
    case CB_BROADCAST: {
        cat_engine_broadcast(e, b->arg0 ? b->arg0 : "");
        return 0;
    }
    case CB_BROADCAST_WAIT: {
        cat_engine_broadcast(e, b->arg0 ? b->arg0 : "");
        fi->waiting_broadcast = true;
        cat_free(fi->waiting_msg);
        fi->waiting_msg = cat_strdup(b->arg0 ? b->arg0 : "");
        return 1;
    }
    case CB_STOP_SCRIPT: fi->done = true; return 1;
    case CB_STOP_ALL: {
        for (size_t i = 0; i < e->fiber_count; ++i) e->fibers[i]->done = true;
        return 1;
    }
    case CB_FOREVER: {
        push_frame(fi, b->children, b->child_count, 1, 0, NULL);
        return 0;
    }
    case CB_REPEAT: {
        CatValue v = slot_or(e, sp, b, "times", 0);
        long n = (long)cat_value_to_number(&v); cat_value_free(&v);
        if (n <= 0) return 0;
        push_frame(fi, b->children, b->child_count, 2, n, NULL);
        return 0;
    }
    case CB_REPEAT_UNTIL: {
        CatFormula *cond = cat_brick_slot(b, "condition");
        push_frame(fi, b->children, b->child_count, 3, 0, cond);
        return 0;
    }
    case CB_IF_BEGIN:
    case CB_IF_THEN_BEGIN: {
        CatValue v = slot_or(e, sp, b, "condition", 0);
        bool t = cat_value_to_bool(&v); cat_value_free(&v);
        if (t)
            push_frame(fi, b->children, b->child_count, 0, 0, NULL);
        else if (b->else_child_count)
            push_frame(fi, b->else_children, b->else_child_count, 0, 0, NULL);
        return 0;
    }
    case CB_SET_VARIABLE: {
        CatValue v = slot_or(e, sp, b, "value", 0);
        cat_sprite_set_var(sp, b->arg0 ? b->arg0 : "", v);
        return 0;
    }
    case CB_CHANGE_VARIABLE: {
        CatValue cur = cat_sprite_get_var(sp, b->arg0 ? b->arg0 : "");
        CatValue by  = slot_or(e, sp, b, "value", 0);
        CatValue nv = cat_value_number(cat_value_to_number(&cur) + cat_value_to_number(&by));
        cat_value_free(&cur); cat_value_free(&by);
        cat_sprite_set_var(sp, b->arg0 ? b->arg0 : "", nv);
        return 0;
    }
    case CB_ADD_TO_LIST: {
        CatList *l = cat_sprite_get_list(sp, b->arg0 ? b->arg0 : "");
        if (l->count == l->cap) { l->cap = l->cap ? l->cap*2 : 4;
            l->items = (CatValue*)cat_realloc(l->items, sizeof(CatValue)*l->cap); }
        l->items[l->count++] = slot_or(e, sp, b, "value", 0);
        return 0;
    }
    case CB_CLEAR_LIST: {
        CatList *l = cat_sprite_get_list(sp, b->arg0 ? b->arg0 : "");
        for (size_t i=0;i<l->count;++i) cat_value_free(&l->items[i]);
        l->count = 0;
        return 0;
    }
    case CB_PLACE_AT: {
        CatValue x = slot_or(e, sp, b, "x", 0), y = slot_or(e, sp, b, "y", 0);
        sp->x = cat_value_to_number(&x); sp->y = cat_value_to_number(&y);
        cat_value_free(&x); cat_value_free(&y);
        return 0;
    }
    case CB_SET_X: { CatValue v=slot_or(e,sp,b,"x",0); sp->x=cat_value_to_number(&v); cat_value_free(&v); return 0; }
    case CB_SET_Y: { CatValue v=slot_or(e,sp,b,"y",0); sp->y=cat_value_to_number(&v); cat_value_free(&v); return 0; }
    case CB_CHANGE_X: { CatValue v=slot_or(e,sp,b,"x",0); sp->x+=cat_value_to_number(&v); cat_value_free(&v); return 0; }
    case CB_CHANGE_Y: { CatValue v=slot_or(e,sp,b,"y",0); sp->y+=cat_value_to_number(&v); cat_value_free(&v); return 0; }
    case CB_MOVE_STEPS: {
        CatValue v=slot_or(e,sp,b,"steps",0);
        double s=cat_value_to_number(&v); cat_value_free(&v);
        double rad = (90.0 - sp->direction) * M_PI/180.0;
        sp->x += s*cos(rad); sp->y += s*sin(rad);
        return 0;
    }
    case CB_TURN_LEFT: { CatValue v=slot_or(e,sp,b,"degrees",0); sp->direction-=cat_value_to_number(&v); cat_value_free(&v); return 0; }
    case CB_TURN_RIGHT:{ CatValue v=slot_or(e,sp,b,"degrees",0); sp->direction+=cat_value_to_number(&v); cat_value_free(&v); return 0; }
    case CB_POINT_IN_DIRECTION:{ CatValue v=slot_or(e,sp,b,"degrees",90); sp->direction=cat_value_to_number(&v); cat_value_free(&v); return 0; }
    case CB_SHOW: sp->visible = true; return 0;
    case CB_HIDE: sp->visible = false; return 0;
    case CB_SET_SIZE_TO: { CatValue v=slot_or(e,sp,b,"size",100); sp->size=cat_value_to_number(&v); cat_value_free(&v); return 0; }
    case CB_CHANGE_SIZE_BY:{ CatValue v=slot_or(e,sp,b,"size",0); sp->size+=cat_value_to_number(&v); cat_value_free(&v); return 0; }
    case CB_SAY: case CB_THINK: {
        CatValue v=slot_or(e,sp,b,"text",0);
        char *s=cat_value_to_cstring(&v);
        printf("[%s %s]: %s\n", sp->name, b->kind==CB_SAY?"says":"thinks", s);
        cat_free(s); cat_value_free(&v); return 0;
    }
    case CB_PLAY_SOUND:
        printf("[%s plays sound: %s]\n", sp->name, b->arg0?b->arg0:"?"); return 0;
    case CB_STOP_ALL_SOUNDS:
        printf("[stop all sounds]\n"); return 0;
    case CB_PRINT: {
        CatValue v=slot_or(e,sp,b,"value",0);
        char *s=cat_value_to_cstring(&v);
        puts(s);
        cat_free(s); cat_value_free(&v); return 0;
    }
    case CB_NOTE: return 0;
    /* Пустые/игнорируемые */
    case CB_LOOP_END: case CB_IF_ELSE: case CB_IF_END: case CB_IF_THEN_END:
        return 0;

    /* ---------- Низкоуровневые C-блоки ---------- */
    case CB_MALLOC: {
        static const char *sz[] = { "csize", "size" };
        size_t n = (size_t)c_slot_num(e, sp, b, sz, 2, 0);
        double addr = c_heap_alloc(&e->heap, n, false);
        if (b->arg0) cat_sprite_set_var(sp, b->arg0, cat_value_number(addr));
        return 0;
    }
    case CB_CALLOC: {
        static const char *cnt[] = { "ccount", "count" };
        static const char *sz[]  = { "csize", "size" };
        size_t c = (size_t)c_slot_num(e, sp, b, cnt, 2, 0);
        size_t s = (size_t)c_slot_num(e, sp, b, sz, 2, 0);
        double addr = (c != 0 && s != 0 && c <= C_HEAP_MAX_BYTES && s <= C_HEAP_MAX_BYTES)
                          ? c_heap_alloc(&e->heap, c * s, true) : 0;
        if (b->arg0) cat_sprite_set_var(sp, b->arg0, cat_value_number(addr));
        return 0;
    }
    case CB_REALLOC: {
        static const char *ptr[] = { "cpointer", "pointer" };
        static const char *sz[]  = { "csize", "size" };
        double old = c_slot_num(e, sp, b, ptr, 2, 0);
        size_t n = (size_t)c_slot_num(e, sp, b, sz, 2, 0);
        double addr = 0;
        if (n > 0) {
            CAlloc *a = c_heap_find(&e->heap, old);
            size_t keep = a ? (a->size < n ? a->size : n) : 0;
            addr = c_heap_alloc(&e->heap, n, true);
            if (addr) {
                CAlloc *na = c_heap_find(&e->heap, addr);
                if (a && keep) memcpy(na->data, a->data, keep);
                if (old) c_heap_free(&e->heap, old);
            }
        } else if (old) {
            c_heap_free(&e->heap, old);
        }
        if (b->arg0) cat_sprite_set_var(sp, b->arg0, cat_value_number(addr));
        return 0;
    }
    case CB_FREE: {
        static const char *ptr[] = { "cpointer", "pointer" };
        double addr = c_slot_num(e, sp, b, ptr, 2, 0);
        c_heap_free(&e->heap, addr);
        return 0;
    }
    case CB_MEMCPY: {
        static const char *dst[] = { "cdestination", "destination", "dest" };
        static const char *src[] = { "csource", "source", "src" };
        static const char *sz[]  = { "csize", "size" };
        double d = c_slot_num(e, sp, b, dst, 3, 0);
        double s = c_slot_num(e, sp, b, src, 3, 0);
        size_t n = (size_t)c_slot_num(e, sp, b, sz, 2, 0);
        CAlloc *da = c_heap_find(&e->heap, d);
        CAlloc *sa = c_heap_find(&e->heap, s);
        if (!da || !sa || n == 0) return 0;
        size_t doff = (size_t)(d - da->addr), soff = (size_t)(s - sa->addr);
        if (doff + n > da->size) n = da->size - doff;
        if (soff + n > sa->size) n = sa->size - soff;
        if (n > 0) memmove(da->data + doff, sa->data + soff, n);
        return 0;
    }
    case CB_MEMSET: {
        static const char *ptr[] = { "cpointer", "pointer" };
        static const char *val[] = { "cvalue", "value" };
        static const char *sz[]  = { "csize", "size" };
        double p = c_slot_num(e, sp, b, ptr, 2, 0);
        int v = (int)c_slot_num(e, sp, b, val, 2, 0);
        size_t n = (size_t)c_slot_num(e, sp, b, sz, 2, 0);
        CAlloc *a = c_heap_find(&e->heap, p);
        if (!a || n == 0) return 0;
        size_t off = (size_t)(p - a->addr);
        if (off + n > a->size) n = a->size - off;
        memset(a->data + off, (unsigned char)v, n);
        return 0;
    }
    case CB_TYPEDEF: {
        static const char *nm[] = { "cname", "name" };
        static const char *bs[] = { "cbasetype", "basetype", "base" };
        char *alias = c_slot_str(e, sp, b, nm, 2);
        char *base = c_slot_str(e, sp, b, bs, 3);
        if (!alias && b->arg0) alias = cat_strdup(b->arg0);
        if (!base && b->arg1) base = cat_strdup(b->arg1);
        if (alias && base) c_typedef_add(e, alias, base);
        cat_free(alias);
        cat_free(base);
        return 0;
    }
    case CB_CAST: {
        static const char *val[] = { "cvalue", "value" };
        static const char *ty[]  = { "ctype", "type" };
        CatFormula *vf = c_slot(b, val, 2);
        char *type = c_slot_str(e, sp, b, ty, 2);
        if (!type && b->arg1) type = cat_strdup(b->arg1);
        if (!type) type = cat_strdup("double");
        CatValue in = vf ? eval_formula_internal(e, sp, vf) : cat_value_number(0);
        CatValue out = c_cast_value(c_resolve_type(e, type), &in);
        cat_value_free(&in);
        cat_free(type);
        if (b->arg0) cat_sprite_set_var(sp, b->arg0, out);
        else cat_value_free(&out);
        return 0;
    }
    case CB_POINTER_SET: {
        static const char *ptr[] = { "cpointer", "pointer" };
        static const char *off[] = { "coffset", "offset" };
        static const char *val[] = { "cvalue", "value" };
        static const char *ty[]  = { "ctype", "type" };
        double p = c_slot_num(e, sp, b, ptr, 2, 0) + c_slot_num(e, sp, b, off, 2, 0);
        CatFormula *vf = c_slot(b, val, 2);
        char *type = c_slot_str(e, sp, b, ty, 2);
        if (!type) type = cat_strdup("double");
        CAlloc *a = c_heap_find(&e->heap, p);
        if (a) {
            size_t n = c_type_size(c_resolve_type(e, type));
            size_t offaddr = (size_t)(p - a->addr);
            if (offaddr + n > a->size) n = a->size - offaddr;
            CatValue v = vf ? eval_formula_internal(e, sp, vf) : cat_value_number(0);
            if (n > 0) c_encode(a->data + offaddr, n, c_resolve_type(e, type), &v);
            cat_value_free(&v);
        }
        cat_free(type);
        return 0;
    }
    case CB_POINTER_GET: {
        static const char *ptr[] = { "cpointer", "pointer" };
        static const char *off[] = { "coffset", "offset" };
        static const char *ty[]  = { "ctype", "type" };
        double p = c_slot_num(e, sp, b, ptr, 2, 0) + c_slot_num(e, sp, b, off, 2, 0);
        char *type = c_slot_str(e, sp, b, ty, 2);
        if (!type) type = cat_strdup("double");
        const char *resolved = c_resolve_type(e, type);
        CAlloc *a = c_heap_find(&e->heap, p);
        CatValue out = cat_value_number(0);
        if (a) {
            size_t n = c_type_size(resolved);
            size_t offaddr = (size_t)(p - a->addr);
            if (offaddr + n > a->size) n = a->size - offaddr;
            if (n > 0) out = c_decode(a->data + offaddr, n, resolved);
        }
        cat_free(type);
        if (b->arg0) cat_sprite_set_var(sp, b->arg0, out);
        else cat_value_free(&out);
        return 0;
    }
    case CB_RETURN:
        fi->done = true;
        return 1;
    case CB_BREAK:
        while (fi->frame_count > 0) {
            Frame *fr = &fi->frames[fi->frame_count - 1];
            fi->frame_count--;
            if (fr->loop_kind != 0) break;
        }
        return 0;
    case CB_CONTINUE:
        while (fi->frame_count > 0) {
            Frame *fr = &fi->frames[fi->frame_count - 1];
            if (fr->loop_kind != 0) { fr->ip = fr->count; break; }
            fi->frame_count--;
        }
        return 0;
    /* Клоны и выполнение произвольного кода: в интерпретаторе (fallback)
       безопасные заглушки. CB_C_CODE/CB_JAVA_CODE требуют компиляции
       (см. cat_compiler.c), CB_CLONE не моделируется в статике. */
    case CB_C_CODE:
    case CB_JAVA_CODE:
    case CB_CLONE:
    case CB_WHEN_CLONED:
        return 0;
    case CB_DELETE_THIS_CLONE:
        fi->done = true;
        return 1;
    default: return 0;
    }
}

bool cat_engine_tick(CatEngine *e, double dt) {
    e->elapsed += dt;
    bool any_alive = false;
    /* Один шаг круговой обработки: каждому фиберу — до одного «yield-brick»
       или пачки быстрых брикков между ними. */
    for (size_t i = 0; i < e->fiber_count; ++i) {
        Fiber *fi = e->fibers[i];
        if (fi->done) continue;

        if (fi->sleep_left > 0) {
            fi->sleep_left -= dt;
            if (fi->sleep_left > 0) { any_alive = true; continue; }
            fi->sleep_left = 0;
        }
        if (fi->waiting_broadcast) { any_alive = true; continue; }

        int guard = 10000; /* защита от бесконечного цикла без yield */
        while (guard--) {
            if (fi->frame_count == 0) { fi->done = true; break; }
            /* Не сохраняем указатель на фрейм между итерациями — push_frame
               делает realloc и указатель протухает. Работаем по индексу. */
            size_t top = fi->frame_count - 1;
            Frame *fr = &fi->frames[top];
            if (fr->ip >= fr->count) {
                if (fr->loop_kind == 1) { fr->ip = 0; break; /* forever - yield */ }
                if (fr->loop_kind == 2) {
                    if (--fr->repeat_left > 0) { fr->ip = 0; continue; }
                    fi->frame_count--; continue;
                }
                if (fr->loop_kind == 3) {
                    CatValue v = fr->cond ? eval_formula_internal(e, fi->sprite, fr->cond) : cat_value_bool(true);
                    bool done = cat_value_to_bool(&v); cat_value_free(&v);
                    if (done) { fi->frame_count--; continue; }
                    fr->ip = 0; continue;
                }
                fi->frame_count--; continue;
            }
            CatBrick *b = fr->bricks[fr->ip++];
            int rc = exec_brick(e, fi, b);
            /* После exec_brick указатель fr мог протухнуть, если был push_frame. */
            if (rc == 1) break;
        }
        if (!fi->done) any_alive = true;
    }
    /* Компакция мёртвых. */
    size_t w = 0;
    for (size_t i = 0; i < e->fiber_count; ++i) {
        if (e->fibers[i]->done) fiber_free(e->fibers[i]);
        else e->fibers[w++] = e->fibers[i];
    }
    e->fiber_count = w;
    return any_alive && e->fiber_count > 0;
}

void cat_engine_run(CatEngine *e, double dt, int max_ticks) {
    for (int i = 0; i < max_ticks; ++i) {
        if (!cat_engine_tick(e, dt)) return;
    }
}

/* ---------- eval ---------- */

static CatValue eval_formula_internal(CatEngine *e, CatSprite *sp, const CatFormula *f) {
    if (!f) return cat_value_number(0);
    switch (f->kind) {
    case CF_NUMBER: case CF_STRING: case CF_BOOL: return cat_value_copy(&f->literal);
    case CF_VARIABLE: {
        char *n = cat_value_to_cstring(&f->literal);
        CatValue v = cat_sprite_get_var(sp, n);
        cat_free(n); return v;
    }
    case CF_LIST: {
        char *n = cat_value_to_cstring(&f->literal);
        CatList *l = sp ? cat_sprite_get_list(sp, n) : NULL;
        cat_free(n);
        if (!l || l->count == 0) return cat_value_string("");
        /* Возвращаем последний элемент как заглушку. */
        return cat_value_copy(&l->items[l->count-1]);
    }
    case CF_SENSOR: {
        char *n = cat_value_to_cstring(&f->literal);
        CatValue v = eval_sensor(sp, n);
        cat_free(n); return v;
    }
    case CF_UNARY_OP: {
        CatValue a = eval_formula_internal(e, sp, f->argc?f->args[0]:NULL);
        return eval_unary(f->op ? f->op : "", a);
    }
    case CF_BINARY_OP: {
        CatValue a = eval_formula_internal(e, sp, f->argc>0?f->args[0]:NULL);
        CatValue b = eval_formula_internal(e, sp, f->argc>1?f->args[1]:NULL);
        return eval_binop(f->op ? f->op : "+", a, b);
    }
    case CF_FUNCTION:
        return eval_function(e, sp, f->op ? f->op : "", f->args, f->argc);
    }
    return cat_value_number(0);
}

CatValue cat_eval_formula(CatEngine *e, CatSprite *sp, const CatFormula *f) {
    return eval_formula_internal(e, sp, f);
}
