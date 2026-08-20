/*
 * cat_mem.h - Управление памятью для C-рантайма.
 *
 * Обёртки над malloc/realloc/free плюс арена (bump allocator) для
 * времени жизни одного проекта. Учёт использованной памяти и опциональный
 * лимит помогают ловить утечки и защищаться от «взбесившихся» скриптов.
 */
#ifndef CAT_MEM_H
#define CAT_MEM_H

#include <stddef.h>

/* Общее использование кучи (только через cat_malloc/free). */
size_t cat_mem_used(void);
size_t cat_mem_peak(void);
size_t cat_mem_alloc_count(void);
size_t cat_mem_free_count(void);

/* 0 = без лимита. */
void   cat_mem_set_limit(size_t bytes);
/* Безопасные настройки выполнения проекта. 0 означает значение по умолчанию. */
size_t cat_mem_limit(void);
void   cat_mem_reset_stats(void);

void  *cat_malloc(size_t n);
void  *cat_calloc(size_t nmemb, size_t size);
void  *cat_realloc(void *p, size_t n);
void   cat_free(void *p);
char  *cat_strdup(const char *s);
char  *cat_strndup(const char *s, size_t n);

/* --- Арена: одноразовый bump-аллокатор. --- */
typedef struct CatArena CatArena;

CatArena *cat_arena_new(size_t initial_capacity);
void     *cat_arena_alloc(CatArena *a, size_t n);
char     *cat_arena_strdup(CatArena *a, const char *s);
void      cat_arena_reset(CatArena *a);
void      cat_arena_free(CatArena *a);

#endif /* CAT_MEM_H */
