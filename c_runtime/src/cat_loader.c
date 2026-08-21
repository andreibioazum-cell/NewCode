#include "cat_loader.h"
#include "cat_xml.h"
#include "cat_mem.h"

#include <stdio.h>
#include <string.h>
#include <strings.h>
#include <stdlib.h>

/* --- список пропущенных брикков (для отчёта) --- */
static char **g_unknown = NULL;
static size_t g_unknown_count = 0;
static size_t g_unknown_cap = 0;

static void note_unknown(const char *name) {
    for (size_t i = 0; i < g_unknown_count; ++i)
        if (strcmp(g_unknown[i], name) == 0) return;
    if (g_unknown_count == g_unknown_cap) {
        g_unknown_cap = g_unknown_cap ? g_unknown_cap * 2 : 16;
        g_unknown = (char **)cat_realloc(g_unknown, sizeof(char *) * g_unknown_cap);
    }
    g_unknown[g_unknown_count++] = cat_strdup(name);
}

size_t cat_loader_last_unknown_count(void) { return g_unknown_count; }
const char *cat_loader_last_unknown_name(size_t i) { return i < g_unknown_count ? g_unknown[i] : NULL; }

static void reset_unknown(void) {
    for (size_t i = 0; i < g_unknown_count; ++i) cat_free(g_unknown[i]);
    cat_free(g_unknown); g_unknown = NULL; g_unknown_count = 0; g_unknown_cap = 0;
}

/* --- Маппинг Catrobat XML имя брикка -> CatBrickKind. Возвращает -1 если неизвестно. --- */
static int map_brick(const char *type) {
    if (!type) return -1;
    struct { const char *n; int k; } M[] = {
        {"StartScript", CB_WHEN_STARTED},
        {"WhenScript", CB_WHEN_TAPPED},
        {"BroadcastScript", CB_WHEN_BROADCAST},
        {"BroadcastReceiverBrick", CB_WHEN_BROADCAST},
        {"BroadcastBrick", CB_BROADCAST},
        {"BroadcastWaitBrick", CB_BROADCAST_WAIT},
        {"WaitBrick", CB_WAIT},
        {"ForeverBrick", CB_FOREVER},
        {"LoopEndBrick", CB_LOOP_END},
        {"LoopEndlessBrick", CB_LOOP_END},
        {"RepeatBrick", CB_REPEAT},
        {"RepeatUntilBrick", CB_REPEAT_UNTIL},
        {"IfLogicBeginBrick", CB_IF_BEGIN},
        {"IfLogicElseBrick", CB_IF_ELSE},
        {"IfLogicEndBrick", CB_IF_END},
        {"IfThenLogicBeginBrick", CB_IF_THEN_BEGIN},
        {"IfThenLogicEndBrick", CB_IF_THEN_END},
        {"StopScriptBrick", CB_STOP_SCRIPT},
        {"NoteBrick", CB_NOTE},
        {"PlaceAtBrick", CB_PLACE_AT},
        {"SetXBrick", CB_SET_X},
        {"SetYBrick", CB_SET_Y},
        {"ChangeXByNBrick", CB_CHANGE_X},
        {"ChangeYByNBrick", CB_CHANGE_Y},
        {"MoveNStepsBrick", CB_MOVE_STEPS},
        {"TurnLeftBrick", CB_TURN_LEFT},
        {"TurnRightBrick", CB_TURN_RIGHT},
        {"PointInDirectionBrick", CB_POINT_IN_DIRECTION},
        {"GlideToBrick", CB_GLIDE_TO},
        {"ShowBrick", CB_SHOW},
        {"HideBrick", CB_HIDE},
        {"SetSizeToBrick", CB_SET_SIZE_TO},
        {"ChangeSizeByNBrick", CB_CHANGE_SIZE_BY},
        {"SayBubbleBrick", CB_SAY}, {"SayBrick", CB_SAY},
        {"SayForBubbleBrick", CB_SAY_FOR},
        {"ThinkBubbleBrick", CB_THINK}, {"ThinkBrick", CB_THINK},
        {"ThinkForBubbleBrick", CB_THINK_FOR},
        {"SetLookBrick", CB_SET_LOOK},
        {"NextLookBrick", CB_NEXT_LOOK},
        {"PreviousLookBrick", CB_PREVIOUS_LOOK},
        {"PlaySoundBrick", CB_PLAY_SOUND},
        {"StopAllSoundsBrick", CB_STOP_ALL_SOUNDS},
        {"SetVolumeToBrick", CB_SET_VOLUME},
        {"ChangeVolumeByNBrick", CB_CHANGE_VOLUME},
        {"SetVariableBrick", CB_SET_VARIABLE},
        {"ChangeVariableBrick", CB_CHANGE_VARIABLE},
        {"AddItemToUserListBrick", CB_ADD_TO_LIST},
        {"DeleteItemOfUserListBrick", CB_DELETE_FROM_LIST},
        {"ClearUserListBrick", CB_CLEAR_LIST},
        {"InsertItemIntoUserListBrick", CB_INSERT_INTO_LIST},
        {"ReplaceItemInUserListBrick", CB_REPLACE_IN_LIST},
        /* Наш собственный удобный: */
        {"PrintBrick", CB_PRINT},
        /* Низкоуровневые C-блоки (см. cat_project.h). */
        {"MallocBrick", CB_MALLOC},
        {"CallocBrick", CB_CALLOC},
        {"ReallocBrick", CB_REALLOC},
        {"FreeBrick", CB_FREE},
        {"MemcpyBrick", CB_MEMCPY},
        {"MemsetBrick", CB_MEMSET},
        {"TypedefBrick", CB_TYPEDEF},
        {"CastBrick", CB_CAST},
        {"PointerSetBrick", CB_POINTER_SET},
        {"PointerGetBrick", CB_POINTER_GET},
        {"ReturnBrick", CB_RETURN},
        {"BreakBrick", CB_BREAK},
        {"ContinueBrick", CB_CONTINUE},
        /* Выполнение произвольного кода (free-text inline). */
        {"ExecuteCCodeBrick", CB_C_CODE},
        {"CCodeBrick", CB_C_CODE},
        {"ExecuteJavaCodeBrick", CB_JAVA_CODE},
        {"JavaCodeBrick", CB_JAVA_CODE},
        /* Клоны спрайтов. */
        {"WhenClonedBrick", CB_WHEN_CLONED},
        {"WhenClonedScript", CB_WHEN_CLONED},
        {"CloneBrick", CB_CLONE},
        {"DeleteThisCloneBrick", CB_DELETE_THIS_CLONE},

        /* --- Расширенный набор языка C --- */
        {"WhileBrick", CB_WHILE},
        {"DoWhileBrick", CB_DO_WHILE},
        {"ForVariableFromToBrick", CB_FOR_FROM_TO},
        {"SwitchBrick", CB_SWITCH},
        {"CaseBrick", CB_CASE},
        {"SwitchEndBrick", CB_SWITCH_END},
        {"CaseBreakBrick", CB_CASE_BREAK},
        {"GotoBrick", CB_GOTO},
        {"LabelBrick", CB_LABEL},
        {"TernaryBrick", CB_TERNARY},
        {"IncrementBrick", CB_INC},
        {"DecrementBrick", CB_DEC},
        {"SizeofBrick", CB_SIZEOF},
        {"StructBrick", CB_STRUCT},
        {"EnumBrick", CB_ENUM},
        {"AssertBrick", CB_ASSERT},
    };
    for (size_t i = 0; i < sizeof(M)/sizeof(M[0]); ++i)
        if (strcmp(M[i].n, type) == 0) return M[i].k;
    return -1;
}

/* --- Парсинг формул из <formulaTree> --- */
static CatFormula *parse_formula(CatXmlNode *n) {
    if (!n) return NULL;
    const char *type = NULL;
    const char *value = NULL;
    for (CatXmlNode *c = n->first_child; c; c = c->next_sibling) {
        if (strcmp(c->tag, "type") == 0) type = c->text;
        else if (strcmp(c->tag, "value") == 0) value = c->text;
    }
    if (!type) type = "NUMBER";
    CatFormula *f = NULL;
    if (strcmp(type, "NUMBER") == 0) {
        f = cat_formula_new(CF_NUMBER);
        f->literal = cat_value_number(value ? atof(value) : 0);
    } else if (strcmp(type, "STRING") == 0) {
        f = cat_formula_new(CF_STRING);
        f->literal = cat_value_string(value ? value : "");
    } else if (strcmp(type, "BOOL") == 0) {
        f = cat_formula_new(CF_BOOL);
        f->literal = cat_value_bool(value && strcmp(value, "1") == 0);
    } else if (strcmp(type, "USER_VARIABLE") == 0) {
        f = cat_formula_new(CF_VARIABLE);
        f->literal = cat_value_string(value ? value : "");
    } else if (strcmp(type, "USER_LIST") == 0) {
        f = cat_formula_new(CF_LIST);
        f->literal = cat_value_string(value ? value : "");
    } else if (strcmp(type, "SENSOR") == 0) {
        f = cat_formula_new(CF_SENSOR);
        f->literal = cat_value_string(value ? value : "");
    } else if (strcmp(type, "OPERATOR") == 0) {
        CatXmlNode *l = cat_xml_child(n, "leftChild");
        CatXmlNode *r = cat_xml_child(n, "rightChild");
        if (l && r) {
            f = cat_formula_new(CF_BINARY_OP);
            f->op = cat_strdup(value ? value : "+");
            f->args = (CatFormula **)cat_calloc(2, sizeof(CatFormula *));
            f->argc = 2;
            f->args[0] = parse_formula(l);
            f->args[1] = parse_formula(r);
        } else if (r) {
            f = cat_formula_new(CF_UNARY_OP);
            f->op = cat_strdup(value ? value : "-");
            f->args = (CatFormula **)cat_calloc(1, sizeof(CatFormula *));
            f->argc = 1;
            f->args[0] = parse_formula(r);
        }
    } else if (strcmp(type, "FUNCTION") == 0) {
        CatXmlNode *l = cat_xml_child(n, "leftChild");
        CatXmlNode *r = cat_xml_child(n, "rightChild");
        f = cat_formula_new(CF_FUNCTION);
        f->op = cat_strdup(value ? value : "");
        size_t argc = (l ? 1 : 0) + (r ? 1 : 0);
        f->args = (CatFormula **)cat_calloc(argc ? argc : 1, sizeof(CatFormula *));
        f->argc = argc;
        size_t k = 0;
        if (l) f->args[k++] = parse_formula(l);
        if (r) f->args[k++] = parse_formula(r);
    } else {
        f = cat_formula_new(CF_NUMBER);
        f->literal = cat_value_number(0);
    }
    return f;
}

static void parse_formula_list(CatXmlNode *host, CatBrick *b) {
    /* Ищем <formulaList><formula category="X"><formulaTree>...</formulaTree></formula></formulaList> */
    CatXmlNode *fl = cat_xml_child(host, "formulaList");
    if (!fl) return;
    for (CatXmlNode *f = fl->first_child; f; f = f->next_sibling) {
        if (strcmp(f->tag, "formula") != 0) continue;
        const char *cat = cat_xml_attr(f, "category");
        /* Поддерживаем оба варианта: <formula><formulaTree>...</formulaTree></formula>
           и упрощённый <formula><type>...</type><value>...</value>...</formula>. */
        CatXmlNode *tree = cat_xml_child(f, "formulaTree");
        CatFormula *cf = parse_formula(tree ? tree : f);
        if (cf) cat_brick_set_slot(b, cat ? cat : "value", cf);
    }
}

/* Ищет по типу брикка и извлекает параметры вроде имени переменной/сообщения. */
static void parse_extras(CatXmlNode *host, CatBrick *b) {
    CatXmlNode *uv = cat_xml_child(host, "userVariable");
    if (uv) {
        const char *ref = cat_xml_attr(uv, "reference");
        if (ref) b->arg0 = cat_strdup(ref); /* упрощение */
        else if (uv->text && uv->text[0]) b->arg0 = cat_strdup(uv->text);
        else {
            /* Формат Android-приложения: <userVariable><name>X</name></userVariable> */
            CatXmlNode *nm = cat_xml_child(uv, "name");
            if (nm && nm->text) b->arg0 = cat_strdup(nm->text);
        }
    }
    CatXmlNode *ul = cat_xml_child(host, "userList");
    if (ul && !b->arg0) {
        if (ul->text) b->arg0 = cat_strdup(ul->text);
    }
    CatXmlNode *bm = cat_xml_child(host, "broadcastMessage");
    if (bm && bm->text) b->arg0 = cat_strdup(bm->text);
    CatXmlNode *sn = cat_xml_child(host, "sound");
    if (sn) {
        CatXmlNode *nm = cat_xml_child(sn, "name");
        if (nm && nm->text) b->arg0 = cat_strdup(nm->text);
    }
    CatXmlNode *lk = cat_xml_child(host, "look");
    if (lk) {
        CatXmlNode *nm = cat_xml_child(lk, "name");
        if (nm && nm->text) b->arg0 = cat_strdup(nm->text);
    }
}

static CatBrick *parse_brick(CatXmlNode *n, bool *is_end_of_block) {
    *is_end_of_block = false;
    const char *type = cat_xml_attr(n, "type");
    if (!type) type = n->tag;
    int k = map_brick(type);
    if (k < 0) { note_unknown(type); return NULL; }
    CatBrick *b = cat_brick_new((CatBrickKind)k);
    parse_formula_list(n, b);
    parse_extras(n, b);
    return b;
}

/* Собирает плоский поток брикков в дерево: сжимает Forever/Repeat/If/While/
   DoWhile/For/Switch ... LoopEnd/IfEnd/SwitchEnd. */
static void build_tree(CatBrick ***flat, size_t *idx, size_t total, CatBrick ***out, size_t *out_n, int stop_at_else) {
    *out = NULL; *out_n = 0;
    while (*idx < total) {
        CatBrick *b = (*flat)[*idx];
        if (!b) { (*idx)++; continue; }
        if (b->kind == CB_LOOP_END || b->kind == CB_IF_END || b->kind == CB_IF_THEN_END ||
            b->kind == CB_SWITCH_END) { (*idx)++; return; }
        if (stop_at_else && b->kind == CB_IF_ELSE) return;
        (*idx)++;
        if (b->kind == CB_FOREVER || b->kind == CB_REPEAT || b->kind == CB_REPEAT_UNTIL ||
            b->kind == CB_WHILE || b->kind == CB_DO_WHILE || b->kind == CB_FOR_FROM_TO) {
            build_tree(flat, idx, total, &b->children, &b->child_count, 0);
        } else if (b->kind == CB_SWITCH) {
            /* тело switch: case-метки и брикки до SwitchEnd */
            build_tree(flat, idx, total, &b->children, &b->child_count, 0);
        } else if (b->kind == CB_IF_BEGIN) {
            build_tree(flat, idx, total, &b->children, &b->child_count, 1);
            if (*idx < total && (*flat)[*idx] && (*flat)[*idx]->kind == CB_IF_ELSE) {
                (*idx)++;
                build_tree(flat, idx, total, &b->else_children, &b->else_child_count, 0);
            }
        } else if (b->kind == CB_IF_THEN_BEGIN) {
            build_tree(flat, idx, total, &b->children, &b->child_count, 0);
        }
        *out = (CatBrick **)cat_realloc(*out, sizeof(CatBrick *) * (*out_n + 1));
        (*out)[(*out_n)++] = b;
    }
}

static CatScript *parse_script(CatXmlNode *n) {
    const char *type = cat_xml_attr(n, "type");
    if (!type) type = n->tag;
    int k = map_brick(type);
    if (k < 0) { note_unknown(type); return NULL; }
    CatBrick *head = cat_brick_new((CatBrickKind)k);
    parse_extras(n, head);
    CatScript *sc = cat_script_new(head);
    /* Плоский список брикков. */
    CatXmlNode *bl = cat_xml_child(n, "brickList");
    if (!bl) return sc;
    CatBrick **flat = NULL; size_t nflat = 0;
    for (CatXmlNode *bn = bl->first_child; bn; bn = bn->next_sibling) {
        if (strcmp(bn->tag, "brick") != 0) continue;
        bool eob = false;
        CatBrick *b = parse_brick(bn, &eob);
        flat = (CatBrick **)cat_realloc(flat, sizeof(CatBrick *) * (nflat + 1));
        flat[nflat++] = b;
    }
    /* Дерево. */
    CatBrick **tree = NULL; size_t ntree = 0; size_t idx = 0;
    build_tree(&flat, &idx, nflat, &tree, &ntree, 0);
    cat_free(flat);
    sc->bricks = tree; sc->brick_count = ntree;
    return sc;
}

static void parse_sprite(CatXmlNode *n, CatScene *scene) {
    CatXmlNode *nm = cat_xml_child(n, "name");
    CatSprite *sp = cat_sprite_new(nm && nm->text ? nm->text : "Sprite");
    /* Скрипты. */
    CatXmlNode *sl = cat_xml_child(n, "scriptList");
    if (sl) {
        for (CatXmlNode *sn = sl->first_child; sn; sn = sn->next_sibling) {
            if (strcmp(sn->tag, "script") != 0) continue;
            CatScript *sc = parse_script(sn);
            if (!sc) continue;
            sp->scripts = (CatScript **)cat_realloc(sp->scripts, sizeof(CatScript *) * (sp->script_count + 1));
            sp->scripts[sp->script_count++] = sc;
        }
    }
    /* Looks / sounds - только имена. */
    CatXmlNode *ll = cat_xml_child(n, "lookList");
    if (ll) for (CatXmlNode *x = ll->first_child; x; x = x->next_sibling) {
        if (strcmp(x->tag, "look") != 0) continue;
        CatXmlNode *ln = cat_xml_child(x, "name");
        CatXmlNode *fn = cat_xml_child(x, "fileName");
        sp->looks = (CatLook *)cat_realloc(sp->looks, sizeof(CatLook) * (sp->look_count + 1));
        sp->looks[sp->look_count].name = cat_strdup(ln && ln->text ? ln->text : "");
        sp->looks[sp->look_count].file_name = cat_strdup(fn && fn->text ? fn->text : "");
        sp->look_count++;
    }
    CatXmlNode *sndl = cat_xml_child(n, "soundList");
    if (sndl) for (CatXmlNode *x = sndl->first_child; x; x = x->next_sibling) {
        if (strcmp(x->tag, "sound") != 0) continue;
        CatXmlNode *ln = cat_xml_child(x, "name");
        CatXmlNode *fn = cat_xml_child(x, "fileName");
        sp->sounds = (CatSound *)cat_realloc(sp->sounds, sizeof(CatSound) * (sp->sound_count + 1));
        sp->sounds[sp->sound_count].name = cat_strdup(ln && ln->text ? ln->text : "");
        sp->sounds[sp->sound_count].file_name = cat_strdup(fn && fn->text ? fn->text : "");
        sp->sound_count++;
    }
    cat_scene_add_sprite(scene, sp);
}

CatProject *cat_load_project_xml_str(const char *src) {
    reset_unknown();
    CatXmlNode *root = cat_xml_parse(src);
    if (!root) return NULL;
    /* Ищем header/name и sceneList/objectList - формат Catrobat различается по версиям. */
    CatXmlNode *header = cat_xml_child(root, "header");
    CatXmlNode *hname = header ? cat_xml_child(header, "programName") : NULL;
    CatProject *p = cat_project_new(hname && hname->text ? hname->text : "Project");

    /* Пробуем sceneList (новый формат). */
    CatXmlNode *sceneList = cat_xml_child(root, "scenes");
    if (!sceneList) sceneList = cat_xml_child(root, "sceneList");
    if (sceneList) {
        for (CatXmlNode *sn = sceneList->first_child; sn; sn = sn->next_sibling) {
            if (strcmp(sn->tag, "scene") != 0) continue;
            CatXmlNode *scnm = cat_xml_child(sn, "name");
            CatScene *scene = cat_scene_new(scnm && scnm->text ? scnm->text : "Scene");
            CatXmlNode *ol = cat_xml_child(sn, "objectList");
            if (!ol) ol = cat_xml_child(sn, "objects");
            if (ol) for (CatXmlNode *on = ol->first_child; on; on = on->next_sibling) {
                if (strcmp(on->tag, "object") != 0) continue;
                parse_sprite(on, scene);
            }
            p->scenes = (CatScene **)cat_realloc(p->scenes, sizeof(CatScene *) * (p->scene_count + 1));
            p->scenes[p->scene_count++] = scene;
        }
    } else {
        /* Старый формат: objectList прямо в корне. */
        CatXmlNode *ol = cat_xml_child(root, "objectList");
        if (ol) {
            CatScene *scene = cat_scene_new("Scene");
            for (CatXmlNode *on = ol->first_child; on; on = on->next_sibling) {
                if (strcmp(on->tag, "object") != 0) continue;
                parse_sprite(on, scene);
            }
            p->scenes = (CatScene **)cat_realloc(p->scenes, sizeof(CatScene *) * (p->scene_count + 1));
            p->scenes[p->scene_count++] = scene;
        }
    }

    cat_xml_free(root);
    return p;
}

CatProject *cat_load_project_xml(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
    if (sz < 0) { fclose(f); return NULL; }
    char *buf = (char *)cat_malloc((size_t)sz + 1);
    size_t rd = fread(buf, 1, (size_t)sz, f); buf[rd] = 0; fclose(f);
    CatProject *p = cat_load_project_xml_str(buf);
    cat_free(buf);
    return p;
}
