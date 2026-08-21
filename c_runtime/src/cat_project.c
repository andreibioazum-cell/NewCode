#include "cat_project.h"
#include "cat_mem.h"

#include <string.h>
#include <stdlib.h>

/* --- Formula --- */

CatFormula *cat_formula_new(CatFormulaKind k) {
    CatFormula *f = (CatFormula *)cat_calloc(1, sizeof(CatFormula));
    f->kind = k;
    f->literal = cat_value_null();
    return f;
}

void cat_formula_free(CatFormula *f) {
    if (!f) return;
    cat_free(f->op);
    cat_value_free(&f->literal);
    for (size_t i = 0; i < f->argc; ++i) cat_formula_free(f->args[i]);
    cat_free(f->args);
    cat_free(f);
}

/* --- Brick --- */

CatBrick *cat_brick_new(CatBrickKind k) {
    CatBrick *b = (CatBrick *)cat_calloc(1, sizeof(CatBrick));
    b->kind = k;
    return b;
}

static void free_children(CatBrick **arr, size_t n) {
    for (size_t i = 0; i < n; ++i) cat_brick_free(arr[i]);
    cat_free(arr);
}

void cat_brick_free(CatBrick *b) {
    if (!b) return;
    for (size_t i = 0; i < b->slot_count; ++i) {
        cat_free(b->slots[i].name);
        cat_formula_free(b->slots[i].value);
    }
    cat_free(b->slots);
    free_children(b->children, b->child_count);
    free_children(b->else_children, b->else_child_count);
    cat_free(b->arg0);
    cat_free(b->arg1);
    cat_free(b);
}

void cat_brick_set_slot(CatBrick *b, const char *name, CatFormula *f) {
    for (size_t i = 0; i < b->slot_count; ++i) {
        if (strcmp(b->slots[i].name, name) == 0) {
            cat_formula_free(b->slots[i].value);
            b->slots[i].value = f;
            return;
        }
    }
    b->slots = (CatSlot *)cat_realloc(b->slots, sizeof(CatSlot) * (b->slot_count + 1));
    b->slots[b->slot_count].name = cat_strdup(name);
    b->slots[b->slot_count].value = f;
    b->slot_count++;
}

CatFormula *cat_brick_slot(const CatBrick *b, const char *name) {
    for (size_t i = 0; i < b->slot_count; ++i)
        if (strcmp(b->slots[i].name, name) == 0) return b->slots[i].value;
    return NULL;
}

/* --- Script --- */

CatScript *cat_script_new(CatBrick *head) {
    CatScript *s = (CatScript *)cat_calloc(1, sizeof(CatScript));
    s->head = head;
    return s;
}

void cat_script_add_brick(CatScript *s, CatBrick *b) {
    s->bricks = (CatBrick **)cat_realloc(s->bricks, sizeof(CatBrick *) * (s->brick_count + 1));
    s->bricks[s->brick_count++] = b;
}

void cat_script_free(CatScript *s) {
    if (!s) return;
    cat_brick_free(s->head);
    for (size_t i = 0; i < s->brick_count; ++i) cat_brick_free(s->bricks[i]);
    cat_free(s->bricks);
    cat_free(s);
}

/* --- Sprite --- */

CatSprite *cat_sprite_new(const char *name) {
    CatSprite *s = (CatSprite *)cat_calloc(1, sizeof(CatSprite));
    s->name = cat_strdup(name ? name : "Sprite");
    s->direction = 90.0;
    s->size = 100.0;
    s->visible = true;
    s->current_look = -1;
    return s;
}

void cat_sprite_free(CatSprite *s) {
    if (!s) return;
    cat_free(s->name);
    for (size_t i = 0; i < s->script_count; ++i) cat_script_free(s->scripts[i]);
    cat_free(s->scripts);
    for (size_t i = 0; i < s->var_count; ++i) {
        cat_free(s->vars[i].name);
        cat_value_free(&s->vars[i].value);
    }
    cat_free(s->vars);
    for (size_t i = 0; i < s->list_count; ++i) {
        cat_free(s->lists[i].name);
        for (size_t j = 0; j < s->lists[i].count; ++j) cat_value_free(&s->lists[i].items[j]);
        cat_free(s->lists[i].items);
    }
    cat_free(s->lists);
    for (size_t i = 0; i < s->look_count; ++i) {
        cat_free(s->looks[i].name);
        cat_free(s->looks[i].file_name);
    }
    cat_free(s->looks);
    for (size_t i = 0; i < s->sound_count; ++i) {
        cat_free(s->sounds[i].name);
        cat_free(s->sounds[i].file_name);
    }
    cat_free(s->sounds);
    cat_free(s);
}

void cat_sprite_set_var(CatSprite *s, const char *name, CatValue v) {
    for (size_t i = 0; i < s->var_count; ++i) {
        if (strcmp(s->vars[i].name, name) == 0) {
            cat_value_free(&s->vars[i].value);
            s->vars[i].value = v;
            return;
        }
    }
    s->vars = (CatVar *)cat_realloc(s->vars, sizeof(CatVar) * (s->var_count + 1));
    s->vars[s->var_count].name = cat_strdup(name);
    s->vars[s->var_count].value = v;
    s->var_count++;
}

CatValue cat_sprite_get_var(CatSprite *s, const char *name) {
    for (size_t i = 0; i < s->var_count; ++i)
        if (strcmp(s->vars[i].name, name) == 0)
            return cat_value_copy(&s->vars[i].value);
    return cat_value_number(0);
}

CatList *cat_sprite_get_list(CatSprite *s, const char *name) {
    for (size_t i = 0; i < s->list_count; ++i)
        if (strcmp(s->lists[i].name, name) == 0) return &s->lists[i];
    /* автосоздание */
    s->lists = (CatList *)cat_realloc(s->lists, sizeof(CatList) * (s->list_count + 1));
    CatList *l = &s->lists[s->list_count++];
    memset(l, 0, sizeof(*l));
    l->name = cat_strdup(name);
    return l;
}

/* --- Scene / Project --- */

CatScene *cat_scene_new(const char *name) {
    CatScene *s = (CatScene *)cat_calloc(1, sizeof(CatScene));
    s->name = cat_strdup(name ? name : "Scene");
    return s;
}

void cat_scene_add_sprite(CatScene *s, CatSprite *sp) {
    s->sprites = (CatSprite **)cat_realloc(s->sprites, sizeof(CatSprite *) * (s->sprite_count + 1));
    s->sprites[s->sprite_count++] = sp;
}

void cat_scene_free(CatScene *s) {
    if (!s) return;
    cat_free(s->name);
    for (size_t i = 0; i < s->sprite_count; ++i) cat_sprite_free(s->sprites[i]);
    cat_free(s->sprites);
    cat_free(s);
}

CatProject *cat_project_new(const char *name) {
    CatProject *p = (CatProject *)cat_calloc(1, sizeof(CatProject));
    p->name = cat_strdup(name ? name : "Project");
    return p;
}

void cat_project_free(CatProject *p) {
    if (!p) return;
    cat_free(p->name);
    for (size_t i = 0; i < p->scene_count; ++i) cat_scene_free(p->scenes[i]);
    cat_free(p->scenes);
    for (size_t i = 0; i < p->var_count; ++i) {
        cat_free(p->vars[i].name);
        cat_value_free(&p->vars[i].value);
    }
    cat_free(p->vars);
    for (size_t i = 0; i < p->list_count; ++i) {
        cat_free(p->lists[i].name);
        for (size_t j = 0; j < p->lists[i].count; ++j) cat_value_free(&p->lists[i].items[j]);
        cat_free(p->lists[i].items);
    }
    cat_free(p->lists);
    cat_free(p);
}

CatValue cat_project_get_var(CatProject *p, const char *name) {
    for (size_t i = 0; i < p->var_count; ++i)
        if (strcmp(p->vars[i].name, name) == 0)
            return cat_value_copy(&p->vars[i].value);
    return cat_value_number(0);
}

void cat_project_set_var(CatProject *p, const char *name, CatValue v) {
    for (size_t i = 0; i < p->var_count; ++i)
        if (strcmp(p->vars[i].name, name) == 0) {
            cat_value_free(&p->vars[i].value);
            p->vars[i].value = v;
            return;
        }
    p->vars = (CatVar *)cat_realloc(p->vars, sizeof(CatVar) * (p->var_count + 1));
    p->vars[p->var_count].name = cat_strdup(name);
    p->vars[p->var_count].value = v;
    p->var_count++;
}

CatList *cat_project_get_list(CatProject *p, const char *name) {
    for (size_t i = 0; i < p->list_count; ++i)
        if (strcmp(p->lists[i].name, name) == 0) return &p->lists[i];
    p->lists = (CatList *)cat_realloc(p->lists, sizeof(CatList) * (p->list_count + 1));
    CatList *l = &p->lists[p->list_count++];
    memset(l, 0, sizeof(*l));
    l->name = cat_strdup(name);
    return l;
}

const char *cat_brick_kind_name(CatBrickKind k) {
    switch (k) {
    case CB_WHEN_STARTED: return "WhenStarted";
    case CB_WHEN_TAPPED: return "WhenTapped";
    case CB_WHEN_BROADCAST: return "WhenBroadcast";
    case CB_BROADCAST: return "Broadcast";
    case CB_BROADCAST_WAIT: return "BroadcastWait";
    case CB_WAIT: return "Wait";
    case CB_FOREVER: return "Forever";
    case CB_LOOP_END: return "LoopEnd";
    case CB_REPEAT: return "Repeat";
    case CB_REPEAT_UNTIL: return "RepeatUntil";
    case CB_IF_BEGIN: return "IfBegin";
    case CB_IF_ELSE: return "IfElse";
    case CB_IF_END: return "IfEnd";
    case CB_IF_THEN_BEGIN: return "IfThenBegin";
    case CB_IF_THEN_END: return "IfThenEnd";
    case CB_STOP_SCRIPT: return "StopScript";
    case CB_STOP_ALL: return "StopAll";
    case CB_STOP_OTHER: return "StopOther";
    case CB_NOTE: return "Note";
    case CB_PLACE_AT: return "PlaceAt";
    case CB_SET_X: return "SetX";
    case CB_SET_Y: return "SetY";
    case CB_CHANGE_X: return "ChangeX";
    case CB_CHANGE_Y: return "ChangeY";
    case CB_MOVE_STEPS: return "MoveSteps";
    case CB_TURN_LEFT: return "TurnLeft";
    case CB_TURN_RIGHT: return "TurnRight";
    case CB_POINT_IN_DIRECTION: return "PointInDirection";
    case CB_GLIDE_TO: return "GlideTo";
    case CB_SHOW: return "Show";
    case CB_HIDE: return "Hide";
    case CB_SET_SIZE_TO: return "SetSize";
    case CB_CHANGE_SIZE_BY: return "ChangeSize";
    case CB_SAY: return "Say";
    case CB_SAY_FOR: return "SayFor";
    case CB_THINK: return "Think";
    case CB_THINK_FOR: return "ThinkFor";
    case CB_SET_LOOK: return "SetLook";
    case CB_NEXT_LOOK: return "NextLook";
    case CB_PREVIOUS_LOOK: return "PreviousLook";
    case CB_PLAY_SOUND: return "PlaySound";
    case CB_STOP_ALL_SOUNDS: return "StopAllSounds";
    case CB_SET_VOLUME: return "SetVolume";
    case CB_CHANGE_VOLUME: return "ChangeVolume";
    case CB_SET_VARIABLE: return "SetVariable";
    case CB_CHANGE_VARIABLE: return "ChangeVariable";
    case CB_ADD_TO_LIST: return "AddToList";
    case CB_DELETE_FROM_LIST: return "DeleteFromList";
    case CB_CLEAR_LIST: return "ClearList";
    case CB_INSERT_INTO_LIST: return "InsertIntoList";
    case CB_REPLACE_IN_LIST: return "ReplaceInList";
    case CB_PRINT: return "Print";
    case CB_MALLOC: return "malloc";
    case CB_CALLOC: return "calloc";
    case CB_REALLOC: return "realloc";
    case CB_FREE: return "free";
    case CB_MEMCPY: return "memcpy";
    case CB_MEMSET: return "memset";
    case CB_TYPEDEF: return "typedef";
    case CB_CAST: return "cast";
    case CB_POINTER_SET: return "pointer set";
    case CB_POINTER_GET: return "pointer get";
    case CB_RETURN: return "return";
    case CB_BREAK: return "break";
    case CB_CONTINUE: return "continue";
    case CB_C_CODE: return "execute C code";
    case CB_JAVA_CODE: return "execute Java code";
    case CB_WHEN_CLONED: return "WhenCloned";
    case CB_CLONE: return "clone";
    case CB_DELETE_THIS_CLONE: return "delete this clone";
    case CB_WHILE: return "while";
    case CB_DO_WHILE: return "do-while";
    case CB_FOR_FROM_TO: return "for (from..to)";
    case CB_SWITCH: return "switch";
    case CB_CASE: return "case";
    case CB_CASE_BREAK: return "break (case)";
    case CB_SWITCH_END: return "switch end";
    case CB_GOTO: return "goto";
    case CB_LABEL: return "label";
    case CB_TERNARY: return "ternary ?:";
    case CB_INC: return "var++";
    case CB_DEC: return "var--";
    case CB_SIZEOF: return "sizeof";
    case CB_STRUCT: return "struct";
    case CB_ENUM: return "enum";
    case CB_ASSERT: return "assert";
    default: return "?";
    }
}
