/*
 * nc_rt.h - Минимальный рантайм для программ, сгенерированных компилятором
 * NewCode. Подключается сгенерированным C-кодом.
 *
 * Принципы:
 *   - значения живут на стеке (NcVal), куча НЕ используется для значений;
 *   - никакой сборки мусора: нет GC, память освобождается явно (free);
 *   - никакого байт-кода и виртуальной машины: весь код ниже компилируется
 *     в нативные машинные инструкции вместе с программой пользователя.
 */
#ifndef NC_RT_H
#define NC_RT_H

/* POSIX-функции (usleep, clock_gettime) под строгим -std=c11 */
#if !defined(_DEFAULT_SOURCE) && !defined(_GNU_SOURCE)
#define _DEFAULT_SOURCE
#endif

#include <math.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#define NC_SBUF 96

/* Значение: число, символ/строка (встроенный буфер) или логическое. */
typedef struct {
    int    str;          /* 1 — строка в s */
    double n;            /* число или адрес указателя */
    char   s[NC_SBUF];   /* короткая строка без кучи */
} NcVal;

/* Фиксированный список без кучи. */
#define NC_LIST_CAP 256
typedef struct {
    NcVal items[NC_LIST_CAP];
    int   count;
} NcList;

static inline NcVal nc_num(double d)            { NcVal v; memset(&v, 0, sizeof v); v.n = d; return v; }
static inline NcVal nc_bool(int b)              { return nc_num(b ? 1.0 : 0.0); }
static inline NcVal nc_str(const char *s)       { NcVal v; memset(&v, 0, sizeof v); if (s) { strncpy(v.s, s, NC_SBUF - 1); v.str = 1; } return v; }

static inline double nc_d(NcVal v)              { return v.str ? atof(v.s) : v.n; }
static inline int    nc_truthy(NcVal v)         { if (v.str) { return v.s[0] != 0 && strcmp(v.s, "false") != 0 && strcmp(v.s, "0") != 0; } return v.n != 0.0; }

/* Числовое значение как строка (в общий буфер tmp). */
static inline const char *nc_str_buf(NcVal v, char tmp[NC_SBUF]) {
    if (v.str) { strncpy(tmp, v.s, NC_SBUF - 1); tmp[NC_SBUF - 1] = 0; return tmp; }
    if (v.n == (long long)v.n) snprintf(tmp, NC_SBUF, "%lld", (long long)v.n);
    else snprintf(tmp, NC_SBUF, "%g", v.n);
    return tmp;
}

/* --- арифметика и сравнения (правила Catrobat: строка-число приводится) --- */
static inline NcVal nc_add(NcVal a, NcVal b)    { if (a.str || b.str) { char t1[NC_SBUF], t2[NC_SBUF]; char r[NC_SBUF * 2]; snprintf(r, sizeof r, "%s%s", nc_str_buf(a, t1), nc_str_buf(b, t2)); r[NC_SBUF - 1] = 0; return nc_str(r); } return nc_num(a.n + b.n); }
static inline NcVal nc_sub(NcVal a, NcVal b)    { return nc_num(nc_d(a) - nc_d(b)); }
static inline NcVal nc_mul(NcVal a, NcVal b)    { return nc_num(nc_d(a) * nc_d(b)); }
static inline NcVal nc_div(NcVal a, NcVal b)    { double d = nc_d(b); return nc_num(d == 0.0 ? 0.0 : nc_d(a) / d); }
static inline NcVal nc_mod(NcVal a, NcVal b)    { double d = nc_d(b); return nc_num(d == 0.0 ? 0.0 : fmod(nc_d(a), d)); }
static inline NcVal nc_pow(NcVal a, NcVal b)    { return nc_num(pow(nc_d(a), nc_d(b))); }
static inline NcVal nc_neg(NcVal a)             { return nc_num(-nc_d(a)); }
static inline NcVal nc_not(NcVal a)             { return nc_bool(!nc_truthy(a)); }
static inline NcVal nc_and(NcVal a, NcVal b)    { return nc_bool(nc_truthy(a) && nc_truthy(b)); }
static inline NcVal nc_or(NcVal a, NcVal b)     { return nc_bool(nc_truthy(a) || nc_truthy(b)); }
static inline NcVal nc_lt(NcVal a, NcVal b)     { return nc_bool(nc_d(a) <  nc_d(b)); }
static inline NcVal nc_gt(NcVal a, NcVal b)     { return nc_bool(nc_d(a) >  nc_d(b)); }
static inline NcVal nc_le(NcVal a, NcVal b)     { return nc_bool(nc_d(a) <= nc_d(b)); }
static inline NcVal nc_ge(NcVal a, NcVal b)     { return nc_bool(nc_d(a) >= nc_d(b)); }
static inline NcVal nc_eq(NcVal a, NcVal b) {
    if (a.str || b.str) { char t1[NC_SBUF], t2[NC_SBUF]; return nc_bool(strcmp(nc_str_buf(a, t1), nc_str_buf(b, t2)) == 0); }
    return nc_bool(a.n == b.n);
}
static inline NcVal nc_ne(NcVal a, NcVal b)     { NcVal e = nc_eq(a, b); return nc_bool(!nc_truthy(e)); }

/* --- математические функции (углы в градусах, как в Catrobat) --- */
static inline double nc_deg(double d)           { return d * M_PI / 180.0; }
static inline NcVal nc_sin(NcVal a)   { return nc_num(sin(nc_deg(nc_d(a)))); }
static inline NcVal nc_cos(NcVal a)   { return nc_num(cos(nc_deg(nc_d(a)))); }
static inline NcVal nc_tan(NcVal a)   { return nc_num(tan(nc_deg(nc_d(a)))); }
static inline NcVal nc_sqrt(NcVal a)  { return nc_num(sqrt(nc_d(a))); }
static inline NcVal nc_abs(NcVal a)   { return nc_num(fabs(nc_d(a))); }
static inline NcVal nc_round(NcVal a) { return nc_num(floor(nc_d(a) + 0.5)); }
static inline NcVal nc_floor(NcVal a) { return nc_num(floor(nc_d(a))); }
static inline NcVal nc_ceil(NcVal a)  { return nc_num(ceil(nc_d(a))); }
static inline NcVal nc_ln(NcVal a)    { return nc_num(log(nc_d(a))); }
static inline NcVal nc_log(NcVal a)   { return nc_num(log10(nc_d(a))); }
static inline NcVal nc_exp(NcVal a)   { return nc_num(exp(nc_d(a))); }
static inline NcVal nc_min(NcVal a, NcVal b) { double x = nc_d(a), y = nc_d(b); return nc_num(x < y ? x : y); }
static inline NcVal nc_max(NcVal a, NcVal b) { double x = nc_d(a), y = nc_d(b); return nc_num(x > y ? x : y); }

static inline void nc_seed(void) { struct timespec ts; clock_gettime(CLOCK_REALTIME, &ts); srand((unsigned)(ts.tv_sec ^ ts.tv_nsec)); }
static inline NcVal nc_rand(NcVal a, NcVal b) {
    double lo = nc_d(a), hi = nc_d(b);
    if (hi < lo) { double t = lo; lo = hi; hi = t; }
    double u = (double)rand() / ((double)RAND_MAX + 1.0);
    return nc_num(lo + u * (hi - lo));
}

/* --- строковые функции --- */
static inline NcVal nc_length(NcVal a) { char t[NC_SBUF]; return nc_num((double)strlen(nc_str_buf(a, t))); }
static inline NcVal nc_join(NcVal a, NcVal b) { char t1[NC_SBUF], t2[NC_SBUF]; char r[NC_SBUF * 2]; snprintf(r, sizeof r, "%s%s", nc_str_buf(a, t1), nc_str_buf(b, t2)); r[NC_SBUF - 1] = 0; return nc_str(r); }
static inline NcVal nc_letter(NcVal idx, NcVal s) {
    char t[NC_SBUF];
    const char *p = nc_str_buf(s, t);
    long i = (long)nc_d(idx), len = (long)strlen(p);
    if (i >= 1 && i <= len) { char c[2] = { p[i - 1], 0 }; return nc_str(c); }
    return nc_str("");
}

/* --- списки (без кучи) --- */
static inline void   nc_list_add(NcList *l, NcVal v)      { if (l->count < NC_LIST_CAP) l->items[l->count++] = v; }
static inline void   nc_list_clear(NcList *l)             { l->count = 0; }
static inline void   nc_list_delete(NcList *l, NcVal idx) { long i = (long)nc_d(idx); if (i >= 1 && i <= l->count) { memmove(&l->items[i - 1], &l->items[i], (size_t)(l->count - i) * sizeof(NcVal)); l->count--; } }
static inline void   nc_list_insert(NcList *l, NcVal idx, NcVal v) { long i = (long)nc_d(idx); if (i < 1) i = 1; if (i > l->count + 1) i = l->count + 1; if (l->count >= NC_LIST_CAP) return; memmove(&l->items[i], &l->items[i - 1], (size_t)(l->count - i + 1) * sizeof(NcVal)); l->items[i - 1] = v; l->count++; }
static inline void   nc_list_replace(NcList *l, NcVal idx, NcVal v) { long i = (long)nc_d(idx); if (i >= 1 && i <= l->count) l->items[i - 1] = v; }
static inline NcVal  nc_list_last(NcList *l)              { return l->count > 0 ? l->items[l->count - 1] : nc_str(""); }

/* --- ввод-вывод --- */
static inline void nc_say(const char *who, NcVal v)   { char t[NC_SBUF]; printf("[%s says]: %s\n", who, nc_str_buf(v, t)); }
static inline void nc_think(const char *who, NcVal v) { char t[NC_SBUF]; printf("[%s thinks]: %s\n", who, nc_str_buf(v, t)); }
static inline void nc_print(NcVal v)                  { char t[NC_SBUF]; puts(nc_str_buf(v, t)); }
static inline void nc_sound(const char *name)         { printf("[plays sound: %s]\n", name ? name : "?"); }

/* --- время --- */
static inline void nc_wait(double seconds) { if (seconds > 0) usleep((unsigned)(seconds * 1000000.0)); }
static inline void nc_tick(void)           { usleep(16000); }
static inline void nc_glide(double *x, double *y, double tx, double ty, double seconds) {
    double sx = *x, sy = *y;
    const double dt = 1.0 / 60.0;
    int steps = (int)(seconds / dt);
    if (steps <= 0) { *x = tx; *y = ty; return; }
    for (int i = 1; i <= steps; ++i) {
        double k = (double)i / (double)steps;
        *x = sx + (tx - sx) * k;
        *y = sy + (ty - sy) * k;
        usleep((unsigned)(dt * 1000000.0));
    }
}

/* --- указатели и C-блоки: НАСТОЯЩИЕ адреса, явные malloc/free, без GC --- */
static inline void *  nc_addr(NcVal p)   { return (void *)(uintptr_t)p.n; }
static inline NcVal   nc_ptr(void *p)    { return nc_num((double)(uintptr_t)p); }
static inline NcVal   nc_malloc(size_t n)      { return nc_ptr(malloc(n)); }
static inline NcVal   nc_calloc(size_t c, size_t n) { return nc_ptr(calloc(c, n)); }
static inline NcVal   nc_realloc(NcVal p, size_t n) { return nc_ptr(realloc(nc_addr(p), n)); }
static inline void    nc_free(NcVal p)         { free(nc_addr(p)); }
static inline void    nc_memcpy(NcVal dst, NcVal src, size_t n) { memmove(nc_addr(dst), nc_addr(src), n); }
static inline void    nc_memset(NcVal p, int byte, size_t n)    { memset(nc_addr(p), byte, n); }

/* Приведения (cast). */
static inline NcVal nc_cast_i(NcVal v)   { double d = nc_d(v); return nc_num(d >= 0 ? floor(d) : ceil(d)); }
static inline NcVal nc_cast_d(NcVal v){ return nc_num(nc_d(v)); }
static inline NcVal nc_cast_f(NcVal v) { return nc_num((double)(float)nc_d(v)); }
static inline NcVal nc_cast_bool(NcVal v)  { return nc_bool(nc_truthy(v)); }
static inline NcVal nc_cast_c(NcVal v)  { char c[2] = { 0, 0 }; if (v.str) c[0] = v.s[0]; else c[0] = (char)nc_d(v); return nc_str(c); }
static inline NcVal nc_cast_l(NcVal v)  { double d = nc_d(v); return nc_num(d >= 0 ? floor(d) : ceil(d)); }
static inline NcVal nc_cast_s(NcVal v)  { return nc_cast_i(v); }
static inline NcVal nc_cast_b(NcVal v)  { return nc_cast_i(v); }
static inline char  nc_chr(NcVal v)        { return v.str ? v.s[0] : (char)nc_d(v); }

/* Запись/чтение по типизированному указателю. */
static inline void nc_pset_d(NcVal p, NcVal off, NcVal v) { *(double *)(nc_addr(p) + (ptrdiff_t)nc_d(off)) = nc_d(v); }
static inline void nc_pset_f(NcVal p, NcVal off, NcVal v) { *(float  *)(nc_addr(p) + (ptrdiff_t)nc_d(off)) = (float)nc_d(v); }
static inline void nc_pset_i(NcVal p, NcVal off, NcVal v) { *(int    *)(nc_addr(p) + (ptrdiff_t)nc_d(off)) = (int)nc_d(v); }
static inline void nc_pset_l(NcVal p, NcVal off, NcVal v) { *(long   *)(nc_addr(p) + (ptrdiff_t)nc_d(off)) = (long)nc_d(v); }
static inline void nc_pset_s(NcVal p, NcVal off, NcVal v) { *(short  *)(nc_addr(p) + (ptrdiff_t)nc_d(off)) = (short)nc_d(v); }
static inline void nc_pset_b(NcVal p, NcVal off, NcVal v) { *(char   *)(nc_addr(p) + (ptrdiff_t)nc_d(off)) = (char)nc_d(v); }
static inline void nc_pset_c(NcVal p, NcVal off, NcVal v) { *(char   *)(nc_addr(p) + (ptrdiff_t)nc_d(off)) = nc_chr(v); }
static inline void nc_pset_bool(NcVal p, NcVal off, NcVal v) { *(char *)(nc_addr(p) + (ptrdiff_t)nc_d(off)) = (char)(nc_truthy(v) ? 1 : 0); }

static inline NcVal nc_pget_d(NcVal p, NcVal off)    { return nc_num(*(double *)(nc_addr(p) + (ptrdiff_t)nc_d(off))); }
static inline NcVal nc_pget_f(NcVal p, NcVal off)    { return nc_num((double)*(float *)(nc_addr(p) + (ptrdiff_t)nc_d(off))); }
static inline NcVal nc_pget_i(NcVal p, NcVal off)    { return nc_num((double)*(int *)(nc_addr(p) + (ptrdiff_t)nc_d(off))); }
static inline NcVal nc_pget_l(NcVal p, NcVal off)    { return nc_num((double)*(long *)(nc_addr(p) + (ptrdiff_t)nc_d(off))); }
static inline NcVal nc_pget_s(NcVal p, NcVal off)    { return nc_num((double)*(short *)(nc_addr(p) + (ptrdiff_t)nc_d(off))); }
static inline NcVal nc_pget_b(NcVal p, NcVal off)    { return nc_num((double)*(unsigned char *)(nc_addr(p) + (ptrdiff_t)nc_d(off))); }
static inline NcVal nc_pget_c(NcVal p, NcVal off)    { char c[2] = { *(char *)(nc_addr(p) + (ptrdiff_t)nc_d(off)), 0 }; return nc_str(c); }
static inline NcVal nc_pget_bool(NcVal p, NcVal off) { return nc_bool(*(char *)(nc_addr(p) + (ptrdiff_t)nc_d(off)) != 0); }

#endif /* NC_RT_H */
