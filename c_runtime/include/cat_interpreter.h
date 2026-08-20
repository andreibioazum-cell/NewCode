/*
 * cat_interpreter.h - Кооперативный интерпретатор Catrobat-проектов на C.
 *
 * Скрипты — это «потоки» (fibers), которые исполняются по кругу
 * (round-robin). Wait/GlideTo/BroadcastWait ставят поток на паузу
 * до определённого момента.
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

/* Отправить широковещательное сообщение. */
void       cat_engine_broadcast(CatEngine *e, const char *msg);

/* Вычислить формулу вне контекста скрипта (для тестов). */
CatValue   cat_eval_formula(CatEngine *e, CatSprite *sp, const CatFormula *f);

#endif
