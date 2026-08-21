/*
 * test_clones.c - Тесты zero-copy клонов интерпретатора NewCode.
 *
 * Проверяем модель: клон делит со спрайтом скрипты и переменные,
 * копирует только позу; «delete this clone» снимает экземпляр;
 * потолок NC_MAX_CLONES защищает от лавины (проект не лагает).
 */
#include "cat_interpreter.h"
#include "cat_loader.h"
#include "cat_mem.h"
#include "cat_project.h"

#include <stdio.h>
#include <string.h>

#define OK(x) do { if (!(x)) { fprintf(stderr, "FAIL: %s (line %d)\n", #x, __LINE__); return 1; } } while(0)

static double sprite_var(CatSprite *sp, const char *name) {
    CatValue v = cat_sprite_get_var(sp, name);
    double d = cat_value_to_number(&v);
    cat_value_free(&v);
    return d;
}

/* XML-помощники: короткая формула-число и формула-сенсор. */
static const char *FNUM =
    "<formula category=\"%s\"><type>NUMBER</type><value>%g</value></formula>";
static const char *FSEN =
    "<formula category=\"%s\"><type>SENSOR</type><value>%s</value></formula>";

/* Клоны выполняют WhenCloned, поза копируется, переменные общие. */
static int test_clone_runs_and_shares(void) {
    char xml[8192];
    char f1[256], f2[256], fx[256], fy[256], fv[256];
    snprintf(f1, sizeof f1, FNUM, "value", 1.0);
    snprintf(f2, sizeof f2, FNUM, "times", 3.0);
    snprintf(fx, sizeof fx, FNUM, "x", 100.0);
    snprintf(fy, sizeof fy, FNUM, "y", 50.0);
    snprintf(fv, sizeof fv, FSEN, "value", "OBJECT_X");
    snprintf(xml, sizeof xml,
      "<program><header><programName>C</programName></header>"
      "<scenes><scene><name>S</name><objectList><object><name>A</name>"
      "<scriptList>"
      "<script type=\"StartScript\"><brickList>"
        "<brick type=\"SetVariableBrick\"><userVariable>n</userVariable>"
          "<formulaList>%s</formulaList></brick>"
        "<brick type=\"PlaceAtBrick\"><formulaList>%s%s</formulaList></brick>"
        "<brick type=\"RepeatBrick\"><formulaList>%s</formulaList></brick>"
        "<brick type=\"CloneBrick\"/>"
        "<brick type=\"LoopEndBrick\"/>"
      "</brickList></script>"
      "<script type=\"WhenClonedScript\"><brickList>"
        "<brick type=\"ChangeVariableBrick\"><userVariable>n</userVariable>"
          "<formulaList>%s</formulaList></brick>"
        "<brick type=\"ChangeVariableBrick\"><userVariable>sx</userVariable>"
          "<formulaList>%s</formulaList></brick>"
      "</brickList></script>"
      "</scriptList></object></objectList></scene></scenes></program>",
      "<formula category=\"value\"><type>NUMBER</type><value>0</value></formula>",
      fx, fy, f2, f1, fv);

    CatProject *p = cat_load_project_xml_str(xml);
    OK(p);
    CatSprite *sp = p->scenes[0]->sprites[0];

    CatEngine *e = cat_engine_new(p);
    cat_engine_start(e);
    cat_engine_run(e, 0.016, 100);

    OK(sprite_var(sp, "n") == 3);      /* каждый клон добавил 1 (переменная общая) */
    OK(sprite_var(sp, "sx") == 300);   /* каждый клон унаследовал x = 100 */
    OK(cat_engine_clone_count(e) == 3);
    OK(cat_engine_instance_count(e) == 4); /* прототип + 3 клона */
    cat_engine_free(e);
    cat_project_free(p);
    return 0;
}

/* «delete this clone» снимает клона до следующих брикков скрипта. */
static int test_delete_this_clone(void) {
    char xml[4096];
    snprintf(xml, sizeof xml,
      "<program><header><programName>D</programName></header>"
      "<scenes><scene><name>S</name><objectList><object><name>A</name>"
      "<scriptList>"
      "<script type=\"StartScript\"><brickList>"
        "<brick type=\"RepeatBrick\"><formulaList>"
        "<formula category=\"times\"><type>NUMBER</type><value>2</value></formula>"
        "</formulaList></brick>"
        "<brick type=\"CloneBrick\"/>"
        "<brick type=\"LoopEndBrick\"/>"
      "</brickList></script>"
      "<script type=\"WhenClonedScript\"><brickList>"
        "<brick type=\"DeleteThisCloneBrick\"/>"
        "<brick type=\"ChangeVariableBrick\"><userVariable>k</userVariable>"
          "<formulaList><formula category=\"value\"><type>NUMBER</type><value>1</value></formula></formulaList></brick>"
      "</brickList></script>"
      "</scriptList></object></objectList></scene></scenes></program>");

    CatProject *p = cat_load_project_xml_str(xml);
    OK(p);
    CatSprite *sp = p->scenes[0]->sprites[0];

    CatEngine *e = cat_engine_new(p);
    cat_engine_start(e);
    cat_engine_run(e, 0.016, 100);

    OK(sprite_var(sp, "k") == 0);            /* клон умер до ChangeVariable */
    OK(cat_engine_clone_count(e) == 0);      /* оба клона собраны в пул */
    OK(cat_engine_instance_count(e) == 1);   /* остался только прототип */
    cat_engine_free(e);
    cat_project_free(p);
    return 0;
}

/* Потолок NC_MAX_CLONES: лавины не случается, движок остаётся живым. */
static int test_clone_cap(void) {
    char xml[2048];
    snprintf(xml, sizeof xml,
      "<program><header><programName>L</programName></header>"
      "<scenes><scene><name>S</name><objectList><object><name>A</name>"
      "<scriptList>"
      "<script type=\"StartScript\"><brickList>"
        "<brick type=\"RepeatBrick\"><formulaList>"
        "<formula category=\"times\"><type>NUMBER</type><value>5000</value></formula>"
        "</formulaList></brick>"
        "<brick type=\"CloneBrick\"/>"
        "<brick type=\"LoopEndBrick\"/>"
      "</brickList></script>"
      "</scriptList></object></objectList></scene></scenes></program>");

    CatProject *p = cat_load_project_xml_str(xml);
    OK(p);
    CatEngine *e = cat_engine_new(p);
    cat_engine_start(e);
    cat_engine_run(e, 0.016, 10);

    OK(cat_engine_clone_count(e) == 1024);       /* NC_MAX_CLONES */
    OK(cat_engine_instance_count(e) == 1025);    /* + прототип */
    cat_engine_free(e);
    cat_project_free(p);
    return 0;
}

int main(void) {
    if (test_clone_runs_and_shares()) return 1;
    if (test_delete_this_clone()) return 1;
    if (test_clone_cap()) return 1;
    printf("test_clones OK; peak mem = %zu\n", cat_mem_peak());
    return 0;
}
