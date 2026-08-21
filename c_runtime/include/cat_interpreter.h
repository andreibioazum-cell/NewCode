/*
 * cat_interpreter.h - Кооперативный интерпретатор проектов NewCode/Catrobat на C (отладочный fallback компилятора).
 *
 * Скрипты — это «потоки» (fibers), которые исполняются по кругу
 * (round-robin). Wait/GlideTo/BroadcastWait ставят поток на паузу
 * до определённого момента.
 *
 * Клоны спрайтов — «zero-copy»: клон делит с прототипом имя, скрипты,
 * переменные и списки; копируется только сценическая поза (x, y, курс,
 * размер, видимость), удалённые клоны возвращаются в пул. Поэтому
 * «клонировать себя в forever» стоит O(1) на клона и не роняет FPS.
 * Потолок NC_MAX_CLONES защищает от лавины клонов.
 */
#ifndef CAT_INTERPRETER_H
#define CAT_INTERPRETER_H

#include "cat_project.h"

typedef struct CatEngine CatEngine;

CatEngine *cat_engine_new(CatProject *project);
void       cat_engine_free(CatEngine *e);

/* Запустить обработчики WhenStarted у всех спрайтов. */
void       cat_engine_start(CatEngine *e);
/* Один тик планировщика; возвращает true, пока есть активные скрипты. */
bool       cat_engine_tick(CatEngine *e, double dt);
/* Прогнать до конца или до max_ticks. */
void       cat_engine_run(CatEngine *e, double dt, int max_ticks);

/* Отправить широковещательное сообщение (получают и клоны). */
void       cat_engine_broadcast(CatEngine *e, const char *msg);

/* Живые экземпляры: прототипы + клоны / только клоны (для тестов). */
size_t     cat_engine_instance_count(const CatEngine *e);
size_t     cat_engine_clone_count(const CatEngine *e);

/* Вычислить формулу вне контекста скрипта (для тестов). */
CatValue   cat_eval_formula(CatEngine *e, CatSprite *sp, const CatFormula *f);

#endif
