/*
 * cat_project.h - Внутреннее представление проекта NewCode/Catrobat.
 *
 * Оставляем ТОЛЬКО базовые сущности: Project -> Scene -> Sprite -> Script -> Brick.
 * Никаких Lego/EV3/NXT/Drone/Phiro/JumpingSumo/Arduino/RaspberryPi/NFC/Cast/Embroidery.
 */
#ifndef CAT_PROJECT_H
#define CAT_PROJECT_H

#include "cat_value.h"
#include <stdbool.h>
#include <stddef.h>

typedef struct CatFormula CatFormula;
typedef struct CatBrick   CatBrick;
typedef struct CatScript  CatScript;
typedef struct CatSprite  CatSprite;
typedef struct CatScene   CatScene;
typedef struct CatProject CatProject;

/* --- Формулы (выражения) --- */
typedef enum {
    CF_NUMBER, CF_STRING, CF_BOOL,
    CF_VARIABLE, CF_LIST,
    CF_SENSOR,            /* встроенные сенсоры/константы (см. cat_sensor.h) */
    CF_UNARY_OP,          /* op: -, not */
    CF_BINARY_OP,         /* +, -, *, /, %, ^, <, >, =, <=, >=, and, or */
    CF_FUNCTION           /* sin, cos, sqrt, random, length, join, letter, ... */
} CatFormulaKind;

struct CatFormula {
    CatFormulaKind kind;
    /* Для BINARY_OP/UNARY_OP/FUNCTION храним операцию как строку. */
    char        *op;
    /* Литерал или имя переменной/списка/сенсора. */
    CatValue     literal;
    /* Ноль, один или несколько подвыражений. */
    CatFormula **args;
    size_t       argc;
};

CatFormula *cat_formula_new(CatFormulaKind k);
void        cat_formula_free(CatFormula *f);

/* --- Брикки. Только базовые, без расширений. --- */
typedef enum {
    /* Управление / события */
    CB_WHEN_STARTED,
    CB_WHEN_TAPPED,
    CB_WHEN_BROADCAST,
    CB_BROADCAST,
    CB_BROADCAST_WAIT,
    CB_WAIT,
    CB_FOREVER, CB_LOOP_END,
    CB_REPEAT,
    CB_REPEAT_UNTIL,
    CB_IF_BEGIN, CB_IF_ELSE, CB_IF_END,
    CB_IF_THEN_BEGIN, CB_IF_THEN_END,
    CB_STOP_SCRIPT, CB_STOP_ALL, CB_STOP_OTHER,
    CB_NOTE,

    /* Движение */
    CB_PLACE_AT, CB_SET_X, CB_SET_Y, CB_CHANGE_X, CB_CHANGE_Y,
    CB_MOVE_STEPS, CB_TURN_LEFT, CB_TURN_RIGHT, CB_POINT_IN_DIRECTION,
    CB_GLIDE_TO,

    /* Внешний вид */
    CB_SHOW, CB_HIDE, CB_SET_SIZE_TO, CB_CHANGE_SIZE_BY,
    CB_SAY, CB_SAY_FOR, CB_THINK, CB_THINK_FOR,
    CB_SET_LOOK, CB_NEXT_LOOK, CB_PREVIOUS_LOOK,

    /* Звук */
    CB_PLAY_SOUND, CB_STOP_ALL_SOUNDS, CB_SET_VOLUME, CB_CHANGE_VOLUME,

    /* Переменные и списки */
    CB_SET_VARIABLE, CB_CHANGE_VARIABLE,
    CB_ADD_TO_LIST, CB_DELETE_FROM_LIST, CB_CLEAR_LIST,
    CB_INSERT_INTO_LIST, CB_REPLACE_IN_LIST,

    /* Печать (базовая, для CLI-исполнения) */
    CB_PRINT,

    /* Низкоуровневые C-блоки. Они не имитируют Java-объекты и
       выполняются тем же C-интерпретатором. */
    CB_MALLOC, CB_CALLOC, CB_REALLOC, CB_FREE,
    CB_MEMCPY, CB_MEMSET,
    CB_TYPEDEF, CB_CAST,
    CB_POINTER_SET, CB_POINTER_GET,
    CB_RETURN, CB_BREAK, CB_CONTINUE,

    /* Выполнение произвольного кода (free-text inline):
       CB_C_CODE встраивает сырой C прямо в сгенерированный вывод
       (выполняется как нативный машинный код вместе с программой);
       CB_JAVA_CODE хранит исходник Java (выполняется скриптовым движком
       в Android-интерпретаторе; в нативной C-компиляции недоступен). */
    CB_C_CODE,
    CB_JAVA_CODE,

    /* Клоны спрайтов. В интерпретаторе — настоящие zero-copy клоны
       (поза своя, скрипты/переменные общие, см. cat_interpreter.c).
       В статической C-компиляции клон выполняет скрипты WhenCloned
       синхронно из фиксированного пула поз (см. cat_compiler.c). */
    CB_WHEN_CLONED,
    CB_CLONE,
    CB_DELETE_THIS_CLONE
} CatBrickKind;

/*
 * Универсальный брикк: тип + именованные слоты формул + строковые параметры.
 * Слот 'value' используется чаще всего, дополнительные — 'x','y','steps','target' и т.п.
 */
typedef struct {
    char       *name;
    CatFormula *value;
} CatSlot;

struct CatBrick {
    CatBrickKind kind;
    CatSlot     *slots;
    size_t       slot_count;
    /* Для составных брикков (if/repeat/forever) - вложенные брикки. */
    CatBrick   **children;
    size_t       child_count;
    /* Для if/else - вторая ветка. */
    CatBrick   **else_children;
    size_t       else_child_count;
    /* Строковые параметры (имя переменной/списка/сообщения/звука/лука). */
    char        *arg0;
    char        *arg1;
};

CatBrick *cat_brick_new(CatBrickKind k);
void      cat_brick_free(CatBrick *b);
void      cat_brick_set_slot(CatBrick *b, const char *name, CatFormula *f);
CatFormula *cat_brick_slot(const CatBrick *b, const char *name);

/* --- Скрипты (стеки брикков) --- */
struct CatScript {
    CatBrick  *head;                /* первый брикк-событие */
    CatBrick **bricks;              /* последующие */
    size_t     brick_count;
};

CatScript *cat_script_new(CatBrick *head);
void       cat_script_add_brick(CatScript *s, CatBrick *b);
void       cat_script_free(CatScript *s);

/* --- Спрайты --- */
typedef struct {
    char    *name;
    CatValue value;
} CatVar;

typedef struct {
    char       *name;
    CatValue   *items;
    size_t      count;
    size_t      cap;
} CatList;

typedef struct {
    char *name;
    char *file_name;
} CatLook;

typedef struct {
    char *name;
    char *file_name;
} CatSound;

struct CatSprite {
    char       *name;
    CatScript **scripts;
    size_t      script_count;
    CatVar     *vars;
    size_t      var_count;
    CatList    *lists;
    size_t      list_count;
    CatLook    *looks;
    size_t      look_count;
    CatSound   *sounds;
    size_t      sound_count;
    /* Состояние сцены/движка. */
    double  x, y, direction, size, transparency, brightness;
    bool    visible;
    int     current_look; /* -1 если нет */
};

CatSprite *cat_sprite_new(const char *name);
void       cat_sprite_free(CatSprite *s);
void       cat_sprite_set_var(CatSprite *s, const char *name, CatValue v);
CatValue   cat_sprite_get_var(CatSprite *s, const char *name);
CatList   *cat_sprite_get_list(CatSprite *s, const char *name);

/* --- Сцены и проект --- */
struct CatScene {
    char        *name;
    CatSprite  **sprites;
    size_t       sprite_count;
};

CatScene *cat_scene_new(const char *name);
void      cat_scene_free(CatScene *s);
void      cat_scene_add_sprite(CatScene *s, CatSprite *sp);

struct CatProject {
    char       *name;
    CatScene  **scenes;
    size_t      scene_count;
    /* Глобальные переменные/списки проекта. */
    CatVar     *vars;
    size_t      var_count;
    CatList    *lists;
    size_t      list_count;
};

CatProject *cat_project_new(const char *name);
void        cat_project_free(CatProject *p);
CatValue    cat_project_get_var(CatProject *p, const char *name);
void        cat_project_set_var(CatProject *p, const char *name, CatValue v);
CatList    *cat_project_get_list(CatProject *p, const char *name);

/* Строковая метка брикка (для отладки/логов). */
const char *cat_brick_kind_name(CatBrickKind k);

#endif /* CAT_PROJECT_H */
