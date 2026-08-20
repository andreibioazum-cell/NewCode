/*
 * newcode - Компилятор и CLI проектов NewCode.
 *
 * Проект Catrobat/XML компилируется в C-исходник, который собирается
 * обычным C-компилятором (cc) в НОСИВНЫЙ МАШИННЫЙ КОД и запускается.
 * Никакого байт-кода, никакой виртуальной машины, никакого GC.
 *
 * Использование:
 *   newcode emit   <code.xml> [-o out.c]          -- сгенерировать C-исходник
 *   newcode build  <code.xml> [-o prog] [-k]      -- сгенерировать + собрать (cc -O2)
 *   newcode run    <code.xml> [-k] [-- args...]   -- собрать и выполнить машинный код
 *   newcode interp <code.xml> [--ticks N ...]     -- (fallback) старый интерпретатор
 */
#define _DEFAULT_SOURCE

#include "cat_compiler.h"
#include "cat_interpreter.h"
#include "cat_loader.h"
#include "cat_mem.h"
#include "cat_project.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>

#include <sys/stat.h>
#include <sys/wait.h>

static void print_usage(const char *argv0) {
    fprintf(stderr,
        "NewCode compiler — проекты Catrobat -> C -> машинный код\n\n"
        "Использование:\n"
        "  %s emit   <code.xml> [-o out.c]\n"
        "  %s build  <code.xml> [-o prog] [-k]\n"
        "  %s run    <code.xml> [-k] [-- arg ...]\n"
        "  %s interp <code.xml> [--ticks N] [--dt SEC] [--quiet]\n"
        "\n"
        "  -k, --keep   не удалять сгенерированные .c/.h (путь печатается)\n",
        argv0, argv0, argv0, argv0);
}

static int write_file(const char *dir, const char *name, const char *content) {
    char path[1024];
    snprintf(path, sizeof path, "%s/%s", dir, name);
    FILE *f = fopen(path, "wb");
    if (!f) {
        fprintf(stderr, "newcode: не могу создать %s\n", path);
        return -1;
    }
    fputs(content, f);
    fclose(f);
    return 0;
}

/* Собрать сгенерированную программу. 0 = успех. */
static int cc_build(const char *dir, const char *out_path) {
    char cmd[2048];
    snprintf(cmd, sizeof cmd,
             "cc -std=c11 -O2 -Wno-unused-function -o \"%s\" \"%s/nc_program.c\" -lm",
             out_path, dir);
    fprintf(stderr, "newcode: %s\n", cmd);
    int rc = system(cmd);
    if (rc == -1) return -1;
    return WEXITSTATUS(rc);
}

static char *load_c_source(const char *xml_path) {
    CatProject *p = cat_load_project_xml(xml_path);
    if (!p) {
        fprintf(stderr, "newcode: не удалось загрузить %s\n", xml_path);
        return NULL;
    }
    size_t un = cat_loader_last_unknown_count();
    if (un) {
        fprintf(stderr, "newcode: предупреждение, пропущено блоков-расширений: %zu\n", un);
        for (size_t i = 0; i < un; ++i)
            fprintf(stderr, "  * %s\n", cat_loader_last_unknown_name(i));
    }
    char *c = cat_compile_to_c(p);
    cat_project_free(p);
    return c;
}

static int cmd_emit(const char *xml, const char *out_c) {
    char *c = load_c_source(xml);
    if (!c) return 1;
    if (out_c && strcmp(out_c, "-") == 0) {
        fputs(c, stdout);
    } else if (out_c) {
        FILE *f = fopen(out_c, "wb");
        if (!f) { fprintf(stderr, "newcode: не могу создать %s\n", out_c); cat_free(c); return 1; }
        fputs(c, f);
        fclose(f);
        fprintf(stderr, "newcode: %s написан (%zu байт C-кода)\n", out_c, strlen(c));
    } else {
        fputs(c, stdout);
    }
    cat_free(c);
    return 0;
}

static int cmd_build_or_run(const char *xml, const char *out_arg, int run, int keep,
                            char **run_args, int run_argc) {
    char *c = load_c_source(xml);
    if (!c) return 1;

    char dir[] = "/tmp/newcodeXXXXXX";
    if (!mkdtemp(dir)) { perror("mkdtemp"); cat_free(c); return 1; }

    if (write_file(dir, "nc_program.c", c) != 0 ||
        write_file(dir, "nc_rt.h", cat_compiler_runtime_header()) != 0) {
        cat_free(c);
        return 1;
    }
    cat_free(c);

    char out_path[1024];
    if (out_arg) snprintf(out_path, sizeof out_path, "%s", out_arg);
    else if (run) snprintf(out_path, sizeof out_path, "%s/nc_program", dir);
    else snprintf(out_path, sizeof out_path, "nc_program");

    if (cc_build(dir, out_path) != 0) {
        fprintf(stderr, "newcode: компиляция не удалась (см. вывод cc выше)\n");
        return 1;
    }

    if (keep) fprintf(stderr, "newcode: исходники: %s/nc_program.c\n", dir);

    int rc = 0;
    if (run) {
        size_t total = strlen(out_path) + 1;
        for (int i = 0; i < run_argc; ++i) total += strlen(run_args[i]) + 3;
        char *cmd2 = (char *)cat_malloc(total + 32);
        strcpy(cmd2, "\"");
        strcat(cmd2, out_path);
        strcat(cmd2, "\"");
        for (int i = 0; i < run_argc; ++i) {
            strcat(cmd2, " \"");
            strcat(cmd2, run_args[i]);
            strcat(cmd2, "\"");
        }
        rc = system(cmd2);
        rc = rc == -1 ? 1 : WEXITSTATUS(rc);
        cat_free(cmd2);
    } else {
        fprintf(stderr, "newcode: готово: %s\n", out_path);
    }

    if (!keep) {
        char cmd3[1100];
        snprintf(cmd3, sizeof cmd3, "rm -rf \"%s\"", dir);
        if (system(cmd3) == -1) {}
    }
    return rc;
}

/* ---- fallback: интерпретатор (прежний режим) ---- */
static int cmd_interp(const char *path, int max_ticks, double dt, int quiet) {
    CatProject *p = cat_load_project_xml(path);
    if (!p) { fprintf(stderr, "Failed to load %s\n", path); return 1; }
    if (!quiet) {
        printf("=== Loaded project: %s ===\n", p->name);
        printf("--- Interpreting (max_ticks=%d, dt=%g) ---\n", max_ticks, dt);
    }
    CatEngine *e = cat_engine_new(p);
    cat_engine_start(e);
    cat_engine_run(e, dt, max_ticks);
    cat_engine_free(e);
    cat_project_free(p);
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 3) { print_usage(argv[0]); return 2; }
    const char *mode = argv[1];
    const char *path = argv[2];
    const char *out = NULL;
    int keep = 0, quiet = 0, max_ticks = 100000;
    double dt = 1.0 / 60.0;

    int i = 3;
    int dashdash = argc; /* индекс первого аргумента после -- */
    for (; i < argc; ++i) {
        if (strcmp(argv[i], "--") == 0) { dashdash = i + 1; break; }
        else if (!strcmp(argv[i], "-o") && i + 1 < argc) out = argv[++i];
        else if (!strcmp(argv[i], "-k") || !strcmp(argv[i], "--keep")) keep = 1;
        else if (!strcmp(argv[i], "--ticks") && i + 1 < argc) max_ticks = atoi(argv[++i]);
        else if (!strcmp(argv[i], "--dt") && i + 1 < argc) dt = atof(argv[++i]);
        else if (!strcmp(argv[i], "--quiet")) quiet = 1;
        else { print_usage(argv[0]); return 2; }
    }

    srand((unsigned)time(NULL));

    if (!strcmp(mode, "emit"))   return cmd_emit(path, out);
    if (!strcmp(mode, "build"))  return cmd_build_or_run(path, out, 0, keep, NULL, 0);
    if (!strcmp(mode, "run"))    return cmd_build_or_run(path, out, 1, keep, argv + dashdash, argc - dashdash);
    if (!strcmp(mode, "interp")) return cmd_interp(path, max_ticks, dt, quiet);

    print_usage(argv[0]);
    return 2;
}
