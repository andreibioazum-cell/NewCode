/*
 * catrobat_run - Автономный CLI-исполнитель проектов Catrobat на C.
 *
 * Использование:
 *   catrobat_run <path/to/code.xml> [--ticks N] [--dt SEC] [--mem-limit BYTES]
 *
 * Поддерживаются только базовые блоки. Расширения (Lego/EV3/NXT/Drone/Phiro/
 * Arduino/RaspberryPi/NFC/Cast/Embroidery/JumpingSumo) молча пропускаются и
 * перечисляются в отчёте по завершении.
 */
#include "cat_project.h"
#include "cat_interpreter.h"
#include "cat_loader.h"
#include "cat_mem.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static void print_usage(const char *argv0) {
    fprintf(stderr,
        "Usage: %s <code.xml> [--ticks N] [--dt SEC] [--mem-limit BYTES] [--quiet]\n",
        argv0);
}

int main(int argc, char **argv) {
    if (argc < 2) { print_usage(argv[0]); return 2; }
    const char *path = argv[1];
    int max_ticks = 100000;
    double dt = 1.0/60.0;
    size_t mem_limit = 0;
    int quiet = 0;
    for (int i = 2; i < argc; ++i) {
        if (!strcmp(argv[i], "--ticks") && i+1<argc) max_ticks = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--dt") && i+1<argc) dt = atof(argv[++i]);
        else if (!strcmp(argv[i], "--mem-limit") && i+1<argc) mem_limit = (size_t)strtoull(argv[++i], NULL, 10);
        else if (!strcmp(argv[i], "--quiet")) quiet = 1;
        else { print_usage(argv[0]); return 2; }
    }
    if (mem_limit) cat_mem_set_limit(mem_limit);
    srand((unsigned)time(NULL));

    CatProject *p = cat_load_project_xml(path);
    if (!p) { fprintf(stderr, "Failed to load %s\n", path); return 1; }

    if (!quiet) {
        printf("=== Loaded project: %s ===\n", p->name);
        printf("Scenes: %zu\n", p->scene_count);
        for (size_t si=0; si<p->scene_count; ++si) {
            CatScene *s = p->scenes[si];
            printf("  Scene %zu: %s (sprites=%zu)\n", si, s->name, s->sprite_count);
            for (size_t j=0;j<s->sprite_count;++j) {
                CatSprite *sp = s->sprites[j];
                printf("    - %s: %zu scripts, %zu looks, %zu sounds\n",
                    sp->name, sp->script_count, sp->look_count, sp->sound_count);
            }
        }
        size_t un = cat_loader_last_unknown_count();
        if (un) {
            printf("Skipped %zu unknown/extension brick types:\n", un);
            for (size_t i=0;i<un;++i) printf("  * %s\n", cat_loader_last_unknown_name(i));
        }
        printf("--- Running (max_ticks=%d, dt=%g) ---\n", max_ticks, dt);
    }

    CatEngine *e = cat_engine_new(p);
    cat_engine_start(e);
    cat_engine_run(e, dt, max_ticks);
    cat_engine_free(e);
    cat_project_free(p);

    if (!quiet) {
        printf("--- Done ---\n");
        printf("Memory: used=%zu peak=%zu allocs=%zu frees=%zu\n",
            cat_mem_used(), cat_mem_peak(), cat_mem_alloc_count(), cat_mem_free_count());
    }
    return 0;
}
