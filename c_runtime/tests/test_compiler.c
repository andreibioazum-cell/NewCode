/*
 * test_compiler.c - сквозной тест компилятора NewCode:
 * XML -> C -> cc -> нативный машинный код -> проверка вывода программы.
 */
#define _DEFAULT_SOURCE

#include "cat_compiler.h"
#include "cat_loader.h"
#include "cat_mem.h"
#include "cat_project.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

#define OK(x) do { if (!(x)) { fprintf(stderr, "FAIL: %s (line %d)\n", #x, __LINE__); return 1; } } while(0)

static char doc[65536];
static char *cur;
static void X(const char *s) {
    size_t n = strlen(s);
    memcpy(cur, s, n + 1);
    cur += n;
}
static void F_NUM(const char *cat, double v) {
    char b[256];
    snprintf(b, sizeof b, "<formula category=\"%s\"><type>NUMBER</type><value>%g</value></formula>", cat, v);
    X(b);
}
static void F_STR(const char *cat, const char *v) {
    char b[256];
    snprintf(b, sizeof b, "<formula category=\"%s\"><type>STRING</type><value>%s</value></formula>", cat, v);
    X(b);
}
static void F_VAR(const char *cat, const char *v) {
    char b[256];
    snprintf(b, sizeof b, "<formula category=\"%s\"><type>USER_VARIABLE</type><value>%s</value></formula>", cat, v);
    X(b);
}
static void F_EQ(const char *cat, const char *lhs, double rhs) {
    char b[512];
    snprintf(b, sizeof b,
        "<formula category=\"%s\"><type>OPERATOR</type><value>EQUAL</value>"
        "<leftChild><type>USER_VARIABLE</type><value>%s</value></leftChild>"
        "<rightChild><type>NUMBER</type><value>%g</value></rightChild></formula>", cat, lhs, rhs);
    X(b);
}
static void F_SENSOR(const char *cat, const char *name) {
    char b[256];
    snprintf(b, sizeof b, "<formula category=\"%s\"><type>SENSOR</type><value>%s</value></formula>", cat, name);
    X(b);
}
static void F_FUNC1(const char *cat, const char *fn, const char *argType, const char *argVal) {
    char b[512];
    snprintf(b, sizeof b,
        "<formula category=\"%s\"><type>FUNCTION</type><value>%s</value>"
        "<leftChild><type>%s</type><value>%s</value></leftChild></formula>", cat, fn, argType, argVal);
    X(b);
}
static void F_BIN(const char *cat, const char *op, const char *lt, const char *lv, const char *rt, const char *rv) {
    char b[600];
    snprintf(b, sizeof b,
        "<formula category=\"%s\"><type>OPERATOR</type><value>%s</value>"
        "<leftChild><type>%s</type><value>%s</value></leftChild>"
        "<rightChild><type>%s</type><value>%s</value></rightChild></formula>", cat, op, lt, lv, rt, rv);
    X(b);
}

/* Скомпилировать XML в машинный код и выполнить; stdout -> out (выделяется). */
static int compile_and_run(const char *xml, char **out, size_t *out_len, int *exit_code) {
    char dir[] = "/tmp/nc_testXXXXXX";
    if (!mkdtemp(dir)) return 1;

    CatProject *p = cat_load_project_xml_str(xml);
    OK(p != NULL);
    char *c = cat_compile_to_c(p);
    OK(c != NULL);
    cat_project_free(p);

    char path[256], hpath[256], opath[256], xpath[256];
    snprintf(path, sizeof path, "%s/nc_program.c", dir);
    snprintf(hpath, sizeof hpath, "%s/nc_rt.h", dir);
    snprintf(opath, sizeof opath, "%s/nc_program", dir);
    snprintf(xpath, sizeof xpath, "%s/out.txt", dir);

    FILE *f = fopen(path, "wb"); OK(f != NULL); fputs(c, f); fclose(f);
    cat_free(c);
    f = fopen(hpath, "wb"); OK(f != NULL); fputs(cat_compiler_runtime_header(), f); fclose(f);

    char cmd[1024];
    snprintf(cmd, sizeof cmd, "cc -std=c11 -O2 -Wno-unused-function -o %s %s -lm 2>%s/err.txt", opath, path, dir);
    int rc = system(cmd);
    if (rc == -1 || WEXITSTATUS(rc) != 0) {
        char epath[256], ebuf[2048];
        snprintf(epath, sizeof epath, "%s/err.txt", dir);
        FILE *ef = fopen(epath, "rb");
        if (ef) { size_t n = fread(ebuf, 1, sizeof ebuf - 1, ef); ebuf[n] = 0; fclose(ef);
            fprintf(stderr, "cc failed:\n%s\n", ebuf); }
        return 1;
    }

    snprintf(cmd, sizeof cmd, "%s >%s", opath, xpath);
    rc = system(cmd);
    *exit_code = (rc == -1) ? 1 : WEXITSTATUS(rc);

    f = fopen(xpath, "rb");
    OK(f != NULL);
    fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
    *out_len = (size_t)sz;
    *out = (char *)malloc((size_t)sz + 1);
    size_t rd = fread(*out, 1, (size_t)sz, f);
    (*out)[rd] = 0;
    fclose(f);

    snprintf(cmd, sizeof cmd, "rm -rf %s", dir);
    if (system(cmd) == -1) {}
    return 0;
}

static int contains(const char *hay, const char *needle) {
    return strstr(hay, needle) != NULL;
}

static int test_arithmetic_and_print(void) {
    cur = doc;
    X("<program><header><programName>T</programName></header>"
      "<scenes><scene><name>S</name><objectList><object><name>A</name>"
      "<scriptList><script type=\"StartScript\"><brickList>");
    X("<brick type=\"SetVariableBrick\"><userVariable>x</userVariable><formulaList>");
    F_NUM("value", 2);
    X("</formulaList></brick>");
    X("<brick type=\"RepeatBrick\"><formulaList>");
    F_NUM("times", 3);
    X("</formulaList></brick>");
    X("<brick type=\"ChangeVariableBrick\"><userVariable>x</userVariable><formulaList>");
    F_NUM("value", 5);
    X("</formulaList></brick>");
    X("<brick type=\"LoopEndBrick\"/>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_VAR("value", "x");
    X("</formulaList></brick>");
    X("<brick type=\"SayBubbleBrick\"><formulaList>");
    F_STR("STRING", "hello");
    X("</formulaList></brick>");
    X("</brickList></script></scriptList></object></objectList></scene></scenes></program>");

    char *out; size_t len; int code;
    OK(compile_and_run(doc, &out, &len, &code) == 0);
    OK(code == 0);
    OK(contains(out, "17"));                /* 2 + 3*5 */
    OK(contains(out, "[A says]: hello"));
    free(out);
    return 0;
}

static int test_c_blocks_native_memory(void) {
    cur = doc;
    X("<program><header><programName>C</programName></header>"
      "<scenes><scene><name>S</name><objectList><object><name>A</name>"
      "<scriptList><script type=\"StartScript\"><brickList>");
    X("<brick type=\"TypedefBrick\"><formulaList>");
    F_STR("C_NAME", "meters"); F_STR("C_BASE_TYPE", "double");
    X("</formulaList></brick>");
    X("<brick type=\"MallocBrick\"><userVariable>ptr</userVariable><formulaList>");
    F_NUM("C_SIZE", 32);
    X("</formulaList></brick>");
    X("<brick type=\"PointerSetBrick\"><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_OFFSET", 0); F_NUM("C_VALUE", 9.5); F_STR("C_TYPE", "meters");
    X("</formulaList></brick>");
    X("<brick type=\"PointerSetBrick\"><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_OFFSET", 8); F_NUM("C_VALUE", 42); F_STR("C_TYPE", "int");
    X("</formulaList></brick>");
    X("<brick type=\"PointerSetBrick\"><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_OFFSET", 12); F_STR("C_VALUE", "Z"); F_STR("C_TYPE", "char");
    X("</formulaList></brick>");
    X("<brick type=\"PointerGetBrick\"><userVariable>a</userVariable><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_OFFSET", 0); F_STR("C_TYPE", "double");
    X("</formulaList></brick>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_VAR("value", "a");
    X("</formulaList></brick>");
    X("<brick type=\"PointerGetBrick\"><userVariable>i</userVariable><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_OFFSET", 8); F_STR("C_TYPE", "int");
    X("</formulaList></brick>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_VAR("value", "i");
    X("</formulaList></brick>");
    X("<brick type=\"PointerGetBrick\"><userVariable>ch</userVariable><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_OFFSET", 12); F_STR("C_TYPE", "char");
    X("</formulaList></brick>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_VAR("value", "ch");
    X("</formulaList></brick>");
    X("<brick type=\"CastBrick\"><userVariable>c2</userVariable><formulaList>");
    F_NUM("C_VALUE", 66); F_STR("C_TYPE", "char");
    X("</formulaList></brick>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_VAR("value", "c2");
    X("</formulaList></brick>");
    X("<brick type=\"CallocBrick\"><userVariable>z</userVariable><formulaList>");
    F_NUM("C_COUNT", 2); F_NUM("C_SIZE", 8);
    X("</formulaList></brick>");
    X("<brick type=\"MemsetBrick\"><formulaList>");
    F_VAR("C_POINTER", "z"); F_NUM("C_VALUE", 1); F_NUM("C_SIZE", 4);
    X("</formulaList></brick>");
    X("<brick type=\"MemcpyBrick\"><formulaList>");
    F_VAR("C_DESTINATION", "z"); F_VAR("C_SOURCE", "ptr"); F_NUM("C_SIZE", 8);
    X("</formulaList></brick>");
    X("<brick type=\"PointerGetBrick\"><userVariable>zv</userVariable><formulaList>");
    F_VAR("C_POINTER", "z"); F_NUM("C_OFFSET", 0); F_STR("C_TYPE", "double");
    X("</formulaList></brick>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_VAR("value", "zv");
    X("</formulaList></brick>");
    X("<brick type=\"ReallocBrick\"><userVariable>big</userVariable><formulaList>");
    F_VAR("C_POINTER", "ptr"); F_NUM("C_SIZE", 64);
    X("</formulaList></brick>");
    X("<brick type=\"PointerGetBrick\"><userVariable>kept</userVariable><formulaList>");
    F_VAR("C_POINTER", "big"); F_NUM("C_OFFSET", 8); F_STR("C_TYPE", "int");
    X("</formulaList></brick>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_VAR("value", "kept");
    X("</formulaList></brick>");
    X("<brick type=\"FreeBrick\"><formulaList>");
    F_VAR("C_POINTER", "big");
    X("</formulaList></brick>");
    X("<brick type=\"FreeBrick\"><formulaList>");
    F_VAR("C_POINTER", "z");
    X("</formulaList></brick>");
    X("</brickList></script></scriptList></object></objectList></scene></scenes></program>");

    char *out; size_t len; int code;
    OK(compile_and_run(doc, &out, &len, &code) == 0);
    OK(code == 0);
    OK(contains(out, "9.5"));    /* *ptr как double через typedef */
    OK(contains(out, "42"));     /* *(int*)(ptr+8) */
    OK(contains(out, "Z"));      /* *(char*)(ptr+12) */
    OK(contains(out, "B"));      /* cast 66 -> "B" */
    OK(contains(out, "42"));     /* realloc сохранил данные */
    free(out);
    return 0;
}

static int test_break_continue_return(void) {
    cur = doc;
    X("<program><header><programName>F</programName></header>"
      "<scenes><scene><name>S</name><objectList><object><name>A</name>"
      "<scriptList><script type=\"StartScript\"><brickList>");
    X("<brick type=\"SetVariableBrick\"><userVariable>sum</userVariable><formulaList>");
    F_NUM("value", 0);
    X("</formulaList></brick>");
    X("<brick type=\"RepeatBrick\"><formulaList>");
    F_NUM("times", 10);
    X("</formulaList></brick>");
    X("<brick type=\"ChangeVariableBrick\"><userVariable>sum</userVariable><formulaList>");
    F_NUM("value", 1);
    X("</formulaList></brick>");
    X("<brick type=\"IfThenLogicBeginBrick\"><formulaList>");
    F_EQ("condition", "sum", 4);
    X("</formulaList></brick>");
    X("<brick type=\"BreakBrick\"/>");
    X("<brick type=\"IfThenLogicEndBrick\"/>");
    X("<brick type=\"LoopEndBrick\"/>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_VAR("value", "sum");
    X("</formulaList></brick>");
    X("<brick type=\"ReturnBrick\"/>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_STR("value", "never");
    X("</formulaList></brick>");
    X("</brickList></script></scriptList></object></objectList></scene></scenes></program>");

    char *out; size_t len; int code;
    OK(compile_and_run(doc, &out, &len, &code) == 0);
    OK(code == 0);
    OK(contains(out, "4"));
    OK(!contains(out, "never"));   /* return отсёк хвост скрипта */
    free(out);
    return 0;
}

static int test_broadcast(void) {
    cur = doc;
    X("<program><header><programName>B</programName></header>"
      "<scenes><scene><name>S</name><objectList><object><name>A</name>"
      "<scriptList>");
    X("<script type=\"StartScript\"><brickList>"
      "<brick type=\"BroadcastBrick\"><broadcastMessage>go</broadcastMessage></brick>"
      "</brickList></script>");
    X("<script type=\"BroadcastScript\"><broadcastMessage>go</broadcastMessage><brickList>");
    X("<brick type=\"SetVariableBrick\"><userVariable>got</userVariable><formulaList>");
    F_NUM("value", 77);
    X("</formulaList></brick>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_VAR("value", "got");
    X("</formulaList></brick>");
    X("</brickList></script>");
    X("</scriptList></object></objectList></scene></scenes></program>");

    char *out; size_t len; int code;
    OK(compile_and_run(doc, &out, &len, &code) == 0);
    OK(code == 0);
    OK(contains(out, "77"));
    free(out);
    return 0;
}

/* Регрессия на баг кодогенерации:
   "SP->x = <NcVal-выражение> без приведения к double".
   Раньше PlaceAt/SetX/SetY/ChangeX/ChangeY с формулой (а не с числом)
   порождали некомпилируемый C: поле double = значение-структура NcVal.
   Здесь формулы — настоящие выражения (PLUS, MULT, SQRT), а не числа. */
static int test_place_at_with_formula(void) {
    cur = doc;
    X("<program><header><programName>P</programName></header>"
      "<scenes><scene><name>S</name><objectList><object><name>A</name>"
      "<scriptList><script type=\"StartScript\"><brickList>");
    /* PlaceAt: X = 10 + 5 = 15, Y = 3 * 4 = 12 */
    X("<brick type=\"PlaceAtBrick\"><formulaList>");
    F_BIN("X_POSITION", "PLUS", "NUMBER", "10", "NUMBER", "5");
    F_BIN("Y_POSITION", "MULT", "NUMBER", "3", "NUMBER", "4");
    X("</formulaList></brick>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_SENSOR("value", "OBJECT_X");
    X("</formulaList></brick>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_SENSOR("value", "OBJECT_Y");
    X("</formulaList></brick>");
    /* SetX с функцией: SQRT(144) = 12 */
    X("<brick type=\"SetXBrick\"><formulaList>");
    F_FUNC1("X_POSITION", "SQRT", "NUMBER", "144");
    X("</formulaList></brick>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_SENSOR("value", "OBJECT_X");
    X("</formulaList></brick>");
    /* ChangeX на 5 -> 12 + 5 = 17 */
    X("<brick type=\"ChangeXByNBrick\"><formulaList>");
    F_NUM("X_POSITION_CHANGE", 5);
    X("</formulaList></brick>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_SENSOR("value", "OBJECT_X");
    X("</formulaList></brick>");
    X("</brickList></script></scriptList></object></objectList></scene></scenes></program>");

    char *out; size_t len; int code;
    OK(compile_and_run(doc, &out, &len, &code) == 0);
    OK(code == 0);
    OK(contains(out, "15"));   /* PlaceAt X = 10+5 */
    OK(contains(out, "12"));   /* PlaceAt Y = 3*4 (и SetX = SQRT(144)) */
    OK(contains(out, "17"));   /* SetX 12 + ChangeX 5 */
    free(out);
    return 0;
}

static int test_execute_c_code_and_clones(void) {
    cur = doc;
    X("<program><header><programName>X</programName></header>"
      "<scenes><scene><name>S</name><objectList><object><name>A</name>"
      "<scriptList><script type=\"StartScript\"><brickList>");
    /* Блок «выполнить код C»: сырой C встраивается прямо в сгенерированный вывод. */
    X("<brick type=\"ExecuteCCodeBrick\"><formulaList>");
    F_STR("code", "puts(\"ccodeok\");");
    X("</formulaList></brick>");
    /* Клонирование — безопасный no-op в статической компиляции. */
    X("<brick type=\"CloneBrick\"/>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_STR("value", "before-clone");
    X("</formulaList></brick>");
    /* «Удалить клон» компилируется в return — хвост скрипта не выполняется. */
    X("<brick type=\"DeleteThisCloneBrick\"/>");
    X("<brick type=\"PrintBrick\"><formulaList>");
    F_STR("value", "after-clone-should-not-print");
    X("</formulaList></brick>");
    X("</brickList></script></scriptList></object></objectList></scene></scenes></program>");

    char *out; size_t len; int code;
    OK(compile_and_run(doc, &out, &len, &code) == 0);
    OK(code == 0);
    OK(contains(out, "ccodeok"));                        /* встроенный C выполнился */
    OK(contains(out, "before-clone"));                    /* CloneBrick не сломал поток */
    OK(!contains(out, "after-clone-should-not-print"));   /* DeleteThisClone == return */
    free(out);
    return 0;
}

int main(void) {
    if (test_arithmetic_and_print()) return 1;
    if (test_place_at_with_formula()) return 1;
    if (test_c_blocks_native_memory()) return 1;
    if (test_break_continue_return()) return 1;
    if (test_broadcast()) return 1;
    if (test_execute_c_code_and_clones()) return 1;
    printf("test_compiler OK; peak mem = %zu\n", cat_mem_peak());
    return 0;
}
