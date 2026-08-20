/*
 * cat_value.h - Тип значений для C-рантайма NewCode.
 *
 * Catrobat оперирует тремя базовыми типами: число (double), строка,
 * логическое. Мы делаем tagged union и сами управляем памятью строк.
 */
#ifndef CAT_VALUE_H
#define CAT_VALUE_H

#include <stddef.h>
#include <stdbool.h>

typedef enum {
    CAT_VAL_NUMBER = 0,
    CAT_VAL_STRING = 1,
    CAT_VAL_BOOL   = 2,
    CAT_VAL_NULL   = 3
} CatValueType;

typedef struct {
    CatValueType type;
    union {
        double   number;
        bool     boolean;
        char    *string;   /* владеющий указатель, освобождается через cat_value_free */
    } as;
} CatValue;

/* --- конструкторы (владеющие) --- */
CatValue cat_value_number(double n);
CatValue cat_value_bool(bool b);
CatValue cat_value_string(const char *s);            /* копирует s */
CatValue cat_value_string_take(char *s);              /* забирает владение */
CatValue cat_value_null(void);

/* --- утилиты --- */
CatValue cat_value_copy(const CatValue *v);
void     cat_value_free(CatValue *v);

/* Приведения типов в стиле Catrobat/Scratch. */
double   cat_value_to_number(const CatValue *v);
bool     cat_value_to_bool(const CatValue *v);
/* Возвращает malloc'нутую строку, которую вызывающий обязан free(). */
char    *cat_value_to_cstring(const CatValue *v);

/* Печать в stdout (для отладки). */
void     cat_value_print(const CatValue *v);

#endif /* CAT_VALUE_H */
