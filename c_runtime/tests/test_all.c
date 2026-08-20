#include "cat_value.h"
#include "cat_mem.h"
#include "cat_project.h"
#include "cat_interpreter.h"
#include "cat_loader.h"
#include "cat_xml.h"

#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>

#define OK(x) do { if (!(x)) { fprintf(stderr, "FAIL: %s (line %d)\n", #x, __LINE__); return 1; } } while(0)

static int test_values(void) {
    CatValue n = cat_value_number(3.14);
    OK(n.type == CAT_VAL_NUMBER);
    OK(cat_value_to_number(&n) == 3.14);
    char *s = cat_value_to_cstring(&n);
    OK(strncmp(s, "3.14", 4) == 0);
    cat_free(s);
    cat_value_free(&n);

    CatValue str = cat_value_string("42");
    OK(cat_value_to_number(&str) == 42);
    OK(cat_value_to_bool(&str) == true);
    cat_value_free(&str);

    CatValue b = cat_value_bool(false);
    OK(cat_value_to_bool(&b) == false);
    cat_value_free(&b);
    return 0;
}

static int test_mem(void) {
    size_t before = cat_mem_used();
    void *p = cat_malloc(128);
    OK(cat_mem_used() == before + 128);
    p = cat_realloc(p, 256);
    OK(cat_mem_used() == before + 256);
    cat_free(p);
    OK(cat_mem_used() == before);

    CatArena *a = cat_arena_new(64);
    char *x = cat_arena_strdup(a, "hello arena");
    OK(strcmp(x, "hello arena") == 0);
    /* принудительно вызвать рост */
    for (int i = 0; i < 100; ++i) cat_arena_alloc(a, 64);
    cat_arena_free(a);
    return 0;
}

static int test_xml(void) {
    const char *src = "<?xml version=\"1.0\"?><root a=\"1\"><child>hi &amp; bye</child></root>";
    CatXmlNode *n = cat_xml_parse(src);
    OK(n != NULL);
    OK(strcmp(n->tag, "root") == 0);
    OK(strcmp(cat_xml_attr(n, "a"), "1") == 0);
    CatXmlNode *c = cat_xml_child(n, "child");
    OK(c && strstr(c->text, "hi & bye") != NULL);
    cat_xml_free(n);
    return 0;
}

static int test_formula(void) {
    /* (2 + 3) * 4 */
    CatFormula *left = cat_formula_new(CF_NUMBER); left->literal = cat_value_number(2);
    CatFormula *right = cat_formula_new(CF_NUMBER); right->literal = cat_value_number(3);
    CatFormula *add = cat_formula_new(CF_BINARY_OP);
    add->op = cat_strdup("+"); add->argc = 2;
    add->args = (CatFormula **)cat_calloc(2, sizeof(CatFormula*));
    add->args[0] = left; add->args[1] = right;

    CatFormula *four = cat_formula_new(CF_NUMBER); four->literal = cat_value_number(4);
    CatFormula *mul = cat_formula_new(CF_BINARY_OP);
    mul->op = cat_strdup("*"); mul->argc = 2;
    mul->args = (CatFormula **)cat_calloc(2, sizeof(CatFormula*));
    mul->args[0] = add; mul->args[1] = four;

    CatValue v = cat_eval_formula(NULL, NULL, mul);
    OK(cat_value_to_number(&v) == 20.0);
    cat_value_free(&v);
    cat_formula_free(mul);
    return 0;
}

static int test_load_and_run(void) {
    const char *xml =
      "<program><header><programName>T</programName></header>"
      "<scenes><scene><name>S</name><objectList><object><name>A</name>"
      "<scriptList><script type=\"StartScript\"><brickList>"
      "<brick type=\"SetVariableBrick\"><userVariable>x</userVariable>"
      "<formulaList><formula category=\"value\">"
      "<type>NUMBER</type><value>10</value></formula></formulaList></brick>"
      "<brick type=\"RepeatBrick\"><formulaList><formula category=\"times\">"
      "<type>NUMBER</type><value>5</value></formula></formulaList></brick>"
      "<brick type=\"ChangeVariableBrick\"><userVariable>x</userVariable>"
      "<formulaList><formula category=\"value\">"
      "<type>NUMBER</type><value>2</value></formula></formulaList></brick>"
      "<brick type=\"LoopEndBrick\"/>"
      "<brick type=\"LegoNxtMotorMoveBrick\"/>"  /* игнорируется */
      "</brickList></script></scriptList></object></objectList></scene></scenes></program>";
    CatProject *p = cat_load_project_xml_str(xml);
    OK(p);
    OK(p->scene_count == 1);
    OK(p->scenes[0]->sprite_count == 1);
    OK(cat_loader_last_unknown_count() >= 1);

    CatEngine *e = cat_engine_new(p);
    cat_engine_start(e);
    cat_engine_run(e, 0.016, 100);
    CatValue x = cat_sprite_get_var(p->scenes[0]->sprites[0], "x");
    OK(cat_value_to_number(&x) == 20.0); /* 10 + 5*2 */
    cat_value_free(&x);
    cat_engine_free(e);
    cat_project_free(p);
    return 0;
}

int main(void) {
    if (test_values()) return 1;
    if (test_mem())    return 1;
    if (test_xml())    return 1;
    if (test_formula())return 1;
    if (test_load_and_run()) return 1;
    printf("test_all OK; peak mem = %zu\n", cat_mem_peak());
    return 0;
}
