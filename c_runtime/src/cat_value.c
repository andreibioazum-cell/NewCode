#include "cat_value.h"
#include "cat_mem.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

CatValue cat_value_number(double n) {
    CatValue v = { .type = CAT_VAL_NUMBER };
    v.as.number = n;
    return v;
}

CatValue cat_value_bool(bool b) {
    CatValue v = { .type = CAT_VAL_BOOL };
    v.as.boolean = b;
    return v;
}

CatValue cat_value_null(void) {
    CatValue v = { .type = CAT_VAL_NULL };
    v.as.number = 0.0;
    return v;
}

CatValue cat_value_string(const char *s) {
    CatValue v = { .type = CAT_VAL_STRING };
    v.as.string = cat_strdup(s ? s : "");
    return v;
}

CatValue cat_value_string_take(char *s) {
    CatValue v = { .type = CAT_VAL_STRING };
    v.as.string = s ? s : cat_strdup("");
    return v;
}

CatValue cat_value_copy(const CatValue *v) {
    if (!v) return cat_value_null();
    switch (v->type) {
    case CAT_VAL_STRING: return cat_value_string(v->as.string);
    default:             return *v;
    }
}

void cat_value_free(CatValue *v) {
    if (!v) return;
    if (v->type == CAT_VAL_STRING) {
        cat_free(v->as.string);
        v->as.string = NULL;
    }
    v->type = CAT_VAL_NULL;
}

double cat_value_to_number(const CatValue *v) {
    if (!v) return 0.0;
    switch (v->type) {
    case CAT_VAL_NUMBER: return v->as.number;
    case CAT_VAL_BOOL:   return v->as.boolean ? 1.0 : 0.0;
    case CAT_VAL_STRING: {
        if (!v->as.string || !*v->as.string) return 0.0;
        char *end = NULL;
        double d = strtod(v->as.string, &end);
        if (end == v->as.string) return 0.0;
        return d;
    }
    case CAT_VAL_NULL: default: return 0.0;
    }
}

bool cat_value_to_bool(const CatValue *v) {
    if (!v) return false;
    switch (v->type) {
    case CAT_VAL_BOOL:   return v->as.boolean;
    case CAT_VAL_NUMBER: return v->as.number != 0.0 && !isnan(v->as.number);
    case CAT_VAL_STRING: return v->as.string && v->as.string[0] != '\0'
                                && strcmp(v->as.string, "false") != 0
                                && strcmp(v->as.string, "0") != 0;
    default:             return false;
    }
}

char *cat_value_to_cstring(const CatValue *v) {
    if (!v) return cat_strdup("");
    char buf[64];
    switch (v->type) {
    case CAT_VAL_STRING: return cat_strdup(v->as.string ? v->as.string : "");
    case CAT_VAL_BOOL:   return cat_strdup(v->as.boolean ? "true" : "false");
    case CAT_VAL_NUMBER: {
        double n = v->as.number;
        if (isnan(n))       return cat_strdup("NaN");
        if (isinf(n))       return cat_strdup(n < 0 ? "-Infinity" : "Infinity");
        /* Целое выводим без хвоста ".0", как это делает Scratch/Catrobat. */
        if (n == (double)(long long)n && n > -1e15 && n < 1e15) {
            snprintf(buf, sizeof buf, "%lld", (long long)n);
        } else {
            snprintf(buf, sizeof buf, "%.10g", n);
        }
        return cat_strdup(buf);
    }
    default: return cat_strdup("");
    }
}

void cat_value_print(const CatValue *v) {
    char *s = cat_value_to_cstring(v);
    fputs(s, stdout);
    cat_free(s);
}
