/*
 * test_c_blocks.c - проверка низкоуровневых C-блоков:
 * malloc/calloc/realloc/free, memcpy/memset, typedef/cast,
 * запись/чтение по указателю, return/break/continue.
 */
#include "cat_value.h"
#include "cat_mem.h"
#include "cat_project.h"
#include "cat_interpreter.h"
#include "cat_loader.h"

#include <math.h>
#include <stdio.h>
#include <string.h>

#define OK(x) do { if (!(x)) { fprintf(stderr, "FAIL: %s (line %d)\n", #x, __LINE__); return 1; } } while(0)

static double num(CatSprite *sp, const char *name) {
    CatValue v = cat_sprite_get_var(sp, name);
    double d = cat_value_to_number(&v);
    cat_value_free(&v);
    return d;
}

/* --- Сборка XML в буфер --- */
static char doc[65536];
static char *cur;
static void X(const char *s) {
    size_t n = strlen(s);
    memcpy(cur, s, n + 1);
    cur += n;
}

/* Формула-число / строка / переменная / равенство в упрощённом виде. */
static void F_NUM(const char *cat, double v) {
    char b[256];
    snprintf(b, sizeof b,
             "<formula category=\"%s\"><type>NUMBER</type><value>%g</value></formula>", cat, v);
    X(b);
}
static void F_STR(const char *cat, const char *v) {
    char b[256];
    snprintf(b, sizeof b,
             "<formula category=\"%s\"><type>STRING</type><value>%s</value></formula>", cat, v);
    X(b);
}
static void F_VAR(const char *cat, const char *v) {
    char b[256];
    snprintf(b, sizeof b,
             "<formula category=\"%s\"><type>USER_VARIABLE</type><value>%s</value></formula>", cat, v);
    X(b);
}
static void F_EQ(const char *cat, const char *lhs, double rhs) {
    char b[512];
    snprintf(b, sizeof b,
        "<formula category=\"%s\"><type>OPERATOR</type><value>EQUAL</value>"
        "<leftChild><type>USER_VARIABLE</type><value>%s</value></leftChild>"
        "<rightChild><type>NUMBER</type><value>%g</value></rightChild>"
        "</formula>", cat, lhs, rhs);
    X(b);
}

static void build_doc(void) {
    cur = doc;
    X("<program><header><programName>CBlocks</programName></header>"
      "<scenes><scene><name>S</name><objectList><object><name>A</name>"
      "<scriptList>");

    /* --- Скрипт 1: память и указатели --- */
    X("<script type=\"StartScript\"><brickList>");
    X("<brick type=\"TypedefBrick\"><formulaList>");
    F_STR("C_NAME", "meters"); F_STR("C_BASE_TYPE", "double");
    X("</formulaList></brick>");
    X("<brick type=\"MallocBrick\"><userVariable>ptr</userVariable><formulaList>");
    F_NUM("C_SIZE", 32);
    X("</formulaList></brick>");
    X("<brick type=\"PointerSetBrick\"><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_OFFSET", 0); F_NUM("C_VALUE", 3.5); F_STR("C_TYPE", "meters");
    X("</formulaList></brick>");
    X("<brick type=\"PointerSetBrick\"><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_OFFSET", 8); F_NUM("C_VALUE", 42); F_STR("C_TYPE", "int");
    X("</formulaList></brick>");
    X("<brick type=\"PointerSetBrick\"><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_OFFSET", 12); F_STR("C_VALUE", "A"); F_STR("C_TYPE", "char");
    X("</formulaList></brick>");
    X("<brick type=\"PointerGetBrick\"><userVariable>a</userVariable><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_OFFSET", 0); F_STR("C_TYPE", "meters");
    X("</formulaList></brick>");
    /* тот же блок, но с «дружественными» именами категорий (алиасы) */
    X("<brick type=\"PointerGetBrick\"><userVariable>i</userVariable><formulaList>");
    F_VAR("pointer", "ptr"); F_NUM("offset", 8); F_STR("type", "int");
    X("</formulaList></brick>");
    X("<brick type=\"PointerGetBrick\"><userVariable>c</userVariable><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_OFFSET", 12); F_STR("C_TYPE", "char");
    X("</formulaList></brick>");
    X("<brick type=\"CallocBrick\"><userVariable>z</userVariable><formulaList>");
    F_NUM("C_COUNT", 4); F_NUM("C_SIZE", 8);
    X("</formulaList></brick>");
    X("<brick type=\"PointerGetBrick\"><userVariable>zval</userVariable><formulaList>");
    F_VAR("C_POINTER", "z"); F_NUM("C_OFFSET", 8); F_STR("C_TYPE", "double");
    X("</formulaList></brick>");
    X("<brick type=\"MemsetBrick\"><formulaList>");
    F_VAR("C_POINTER", "z"); F_NUM("C_VALUE", 1); F_NUM("C_SIZE", 4);
    X("</formulaList></brick>");
    X("<brick type=\"PointerGetBrick\"><userVariable>zset</userVariable><formulaList>");
    F_VAR("C_POINTER", "z"); F_NUM("C_OFFSET", 0); F_STR("C_TYPE", "int");
    X("</formulaList></brick>");
    X("<brick type=\"MemcpyBrick\"><formulaList>");
    F_VAR("C_DESTINATION", "z"); F_VAR("C_SOURCE", "ptr"); F_NUM("C_SIZE", 8);
    X("</formulaList></brick>");
    X("<brick type=\"PointerGetBrick\"><userVariable>zcp</userVariable><formulaList>");
    F_VAR("C_POINTER", "z"); F_NUM("C_OFFSET", 0); F_STR("C_TYPE", "double");
    X("</formulaList></brick>");
    X("<brick type=\"ReallocBrick\"><userVariable>big</userVariable><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_SIZE", 64);
    X("</formulaList></brick>");
    X("<brick type=\"PointerGetBrick\"><userVariable>kept</userVariable><formulaList>");
    F_VAR("C_POINTER", "big"); F_NUM("C_OFFSET", 8); F_STR("C_TYPE", "int");
    X("</formulaList></brick>");
    X("<brick type=\"CastBrick\"><userVariable>ci</userVariable><formulaList>");
    F_NUM("C_VALUE", 3.99); F_STR("C_TYPE", "int");
    X("</formulaList></brick>");
    X("<brick type=\"CastBrick\"><userVariable>cc</userVariable><formulaList>");
    F_NUM("C_VALUE", 66); F_STR("C_TYPE", "char");
    X("</formulaList></brick>");
    /* Android-формат переменной: <userVariable><name>...</name></userVariable> */
    X("<brick type=\"CastBrick\"><userVariable><name>nested</name></userVariable><formulaList>");
    F_NUM("C_VALUE", 7.7); F_STR("C_TYPE", "int");
    X("</formulaList></brick>");
    X("<brick type=\"MallocBrick\"><userVariable>nullp</userVariable><formulaList>");
    F_NUM("C_SIZE", 0);
    X("</formulaList></brick>");
    X("<brick type=\"FreeBrick\"><formulaList>");
    F_VAR("C_POINTER", "big");
    X("</formulaList></brick>");
    X("</brickList></script>");

    /* --- Скрипт 2: break --- */
    X("<script type=\"StartScript\"><brickList>");
    X("<brick type=\"SetVariableBrick\"><userVariable>sum1</userVariable><formulaList>");
    F_NUM("value", 0);
    X("</formulaList></brick>");
    X("<brick type=\"RepeatBrick\"><formulaList>");
    F_NUM("times", 10);
    X("</formulaList></brick>");
    X("<brick type=\"ChangeVariableBrick\"><userVariable>sum1</userVariable><formulaList>");
    F_NUM("value", 1);
    X("</formulaList></brick>");
    X("<brick type=\"IfThenLogicBeginBrick\"><formulaList>");
    F_EQ("condition", "sum1", 4);
    X("</formulaList></brick>");
    X("<brick type=\"BreakBrick\"/>");
    X("<brick type=\"IfThenLogicEndBrick\"/>");
    X("<brick type=\"LoopEndBrick\"/>");
    X("<brick type=\"SetVariableBrick\"><userVariable>after1</userVariable><formulaList>");
    F_NUM("value", 7);
    X("</formulaList></brick>");
    X("</brickList></script>");

    /* --- Скрипт 3: continue --- */
    X("<script type=\"StartScript\"><brickList>");
    X("<brick type=\"SetVariableBrick\"><userVariable>cnt</userVariable><formulaList>");
    F_NUM("value", 0);
    X("</formulaList></brick>");
    X("<brick type=\"SetVariableBrick\"><userVariable>hits</userVariable><formulaList>");
    F_NUM("value", 0);
    X("</formulaList></brick>");
    X("<brick type=\"RepeatBrick\"><formulaList>");
    F_NUM("times", 10);
    X("</formulaList></brick>");
    X("<brick type=\"ChangeVariableBrick\"><userVariable>cnt</userVariable><formulaList>");
    F_NUM("value", 1);
    X("</formulaList></brick>");
    X("<brick type=\"IfThenLogicBeginBrick\"><formulaList>");
    F_EQ("condition", "cnt", 1);
    X("</formulaList></brick>");
    X("<brick type=\"ContinueBrick\"/>");
    X("<brick type=\"IfThenLogicEndBrick\"/>");
    X("<brick type=\"ChangeVariableBrick\"><userVariable>hits</userVariable><formulaList>");
    F_NUM("value", 1);
    X("</formulaList></brick>");
    X("<brick type=\"LoopEndBrick\"/>");
    X("</brickList></script>");

    /* --- Скрипт 4: return --- */
    X("<script type=\"StartScript\"><brickList>");
    X("<brick type=\"SetVariableBrick\"><userVariable>ret</userVariable><formulaList>");
    F_NUM("value", 1);
    X("</formulaList></brick>");
    X("<brick type=\"ReturnBrick\"/>");
    X("<brick type=\"SetVariableBrick\"><userVariable>ret</userVariable><formulaList>");
    F_NUM("value", 100);
    X("</formulaList></brick>");
    X("</brickList></script>");

    X("</scriptList></object></objectList></scene></scenes></program>");
}

static int run_and_check(void) {
    build_doc();
    CatProject *p = cat_load_project_xml_str(doc);
    OK(p != NULL);
    CatEngine *e = cat_engine_new(p);
    cat_engine_start(e);
    cat_engine_run(e, 0.016, 100);
    CatSprite *sp = p->scenes[0]->sprites[0];

    /* Память: указатель выдан и данные читаются корректно. */
    double ptr = num(sp, "ptr");
    OK(ptr >= 4096.0);
    OK(num(sp, "a") == 3.5);            /* double через typedef "meters" */
    OK(num(sp, "i") == 42.0);           /* int, имена-алиасы категорий работают */
    CatValue c = cat_sprite_get_var(sp, "c");
    OK(c.type == CAT_VAL_STRING && c.as.string && strcmp(c.as.string, "A") == 0);
    cat_value_free(&c);

    OK(num(sp, "z") >= 4096.0);         /* calloc выдан */
    OK(num(sp, "zval") == 0.0);         /* calloc обнулён */
    OK(num(sp, "zset") == 0x01010101);  /* memset байтом 1 x4, чтение int */
    OK(num(sp, "zcp") == 3.5);          /* memcpy скопировал double 3.5 */

    OK(num(sp, "big") >= 4096.0);       /* realloc: новый адрес */
    OK(num(sp, "big") != ptr);          /* и он отличается от старого */
    OK(num(sp, "kept") == 42.0);        /* данные пережили realloc */

    OK(num(sp, "ci") == 3.0);           /* cast к int обрезает дробную часть */
    CatValue cc = cat_sprite_get_var(sp, "cc");
    OK(cc.type == CAT_VAL_STRING && cc.as.string && strcmp(cc.as.string, "B") == 0); /* cast 66 -> 'B' */
    cat_value_free(&cc);

    OK(num(sp, "nullp") == 0.0);        /* malloc(0) == NULL */
    OK(num(sp, "nested") == 7.0);       /* вложенный формат <userVariable><name> */

    /* Управление потоком. */
    OK(num(sp, "sum1") == 4.0);         /* break вышел из цикла на 4-й итерации */
    OK(num(sp, "after1") == 7.0);       /* код после цикла выполнился */
    OK(num(sp, "cnt") == 10.0);         /* continue не прервал цикл */
    OK(num(sp, "hits") == 9.0);         /* пропущена ровно одна итерация */
    OK(num(sp, "ret") == 1.0);          /* return завершил скрипт */

    cat_engine_free(e);
    cat_project_free(p);
    return 0;
}

int main(void) {
    if (run_and_check()) return 1;
    printf("test_c_blocks OK; peak mem = %zu\n", cat_mem_peak());
    return 0;
}
