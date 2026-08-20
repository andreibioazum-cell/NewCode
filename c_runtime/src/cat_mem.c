#define _POSIX_C_SOURCE 200809L
#include "cat_mem.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static size_t g_used     = 0;
static size_t g_peak     = 0;
static size_t g_allocs   = 0;
static size_t g_frees    = 0;
static size_t g_limit    = 0;   /* 0 -> без лимита */

/* Храним размер блока в префиксе, чтобы уметь считать free. */
typedef struct { size_t size; } Header;

static void oom(size_t want) {
    fprintf(stderr, "cat_runtime: OOM (запросили %zu байт, лимит %zu, использовано %zu)\n",
            want, g_limit, g_used);
    abort();
}

size_t cat_mem_used(void)         { return g_used; }
size_t cat_mem_peak(void)         { return g_peak; }
size_t cat_mem_alloc_count(void)  { return g_allocs; }
size_t cat_mem_free_count(void)   { return g_frees; }
void   cat_mem_set_limit(size_t b){ g_limit = b; }

void *cat_malloc(size_t n) {
    if (n == 0) n = 1;
    if (g_limit && g_used + n + sizeof(Header) > g_limit) oom(n);
    Header *h = (Header *)malloc(sizeof(Header) + n);
    if (!h) oom(n);
    h->size = n;
    g_used += n;
    if (g_used > g_peak) g_peak = g_used;
    g_allocs++;
    return (void *)(h + 1);
}

void *cat_calloc(size_t nmemb, size_t size) {
    size_t total = nmemb * size;
    void *p = cat_malloc(total);
    memset(p, 0, total);
    return p;
}

void *cat_realloc(void *p, size_t n) {
    if (!p) return cat_malloc(n);
    if (n == 0) { cat_free(p); return NULL; }
    Header *h = ((Header *)p) - 1;
    size_t old = h->size;
    if (g_limit && g_used - old + n + sizeof(Header) > g_limit) oom(n);
    Header *nh = (Header *)realloc(h, sizeof(Header) + n);
    if (!nh) oom(n);
    nh->size = n;
    g_used = g_used - old + n;
    if (g_used > g_peak) g_peak = g_used;
    return (void *)(nh + 1);
}

void cat_free(void *p) {
    if (!p) return;
    Header *h = ((Header *)p) - 1;
    g_used -= h->size;
    g_frees++;
    free(h);
}

char *cat_strdup(const char *s) {
    if (!s) s = "";
    size_t n = strlen(s);
    char *r = (char *)cat_malloc(n + 1);
    memcpy(r, s, n + 1);
    return r;
}

char *cat_strndup(const char *s, size_t n) {
    if (!s) s = "";
    size_t l = strnlen(s, n);
    char *r = (char *)cat_malloc(l + 1);
    memcpy(r, s, l);
    r[l] = '\0';
    return r;
}

/* --- Арена --- */

typedef struct ArenaBlock {
    struct ArenaBlock *next;
    size_t cap;
    size_t used;
    /* данные идут сразу за структурой */
} ArenaBlock;

struct CatArena {
    ArenaBlock *head;
    size_t      block_size;
};

static ArenaBlock *arena_new_block(size_t cap) {
    ArenaBlock *b = (ArenaBlock *)cat_malloc(sizeof(ArenaBlock) + cap);
    b->next = NULL;
    b->cap  = cap;
    b->used = 0;
    return b;
}

CatArena *cat_arena_new(size_t initial_capacity) {
    if (initial_capacity < 1024) initial_capacity = 4096;
    CatArena *a = (CatArena *)cat_malloc(sizeof(CatArena));
    a->block_size = initial_capacity;
    a->head = arena_new_block(initial_capacity);
    return a;
}

void *cat_arena_alloc(CatArena *a, size_t n) {
    /* Выравнивание по 8. */
    n = (n + 7u) & ~(size_t)7u;
    ArenaBlock *b = a->head;
    if (b->used + n > b->cap) {
        size_t cap = a->block_size;
        if (n > cap) cap = n;
        ArenaBlock *nb = arena_new_block(cap);
        nb->next = b;
        a->head = nb;
        b = nb;
    }
    void *p = (char *)(b + 1) + b->used;
    b->used += n;
    return p;
}

char *cat_arena_strdup(CatArena *a, const char *s) {
    if (!s) s = "";
    size_t n = strlen(s) + 1;
    char *r = (char *)cat_arena_alloc(a, n);
    memcpy(r, s, n);
    return r;
}

void cat_arena_reset(CatArena *a) {
    ArenaBlock *b = a->head;
    while (b && b->next) {
        ArenaBlock *n = b->next;
        cat_free(b);
        b = n;
    }
    if (b) { b->used = 0; a->head = b; }
}

void cat_arena_free(CatArena *a) {
    if (!a) return;
    ArenaBlock *b = a->head;
    while (b) { ArenaBlock *n = b->next; cat_free(b); b = n; }
    cat_free(a);
}
