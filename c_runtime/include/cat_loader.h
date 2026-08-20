/*
 * cat_loader.h - Загрузчик Catrobat code.xml в CatProject.
 * Поддерживает подмножество формата достаточное для базовых брикков.
 * Неизвестные брикки и расширения (Lego/Drone/Raspi/NFC/...) молча пропускаются.
 */
#ifndef CAT_LOADER_H
#define CAT_LOADER_H

#include "cat_project.h"

CatProject *cat_load_project_xml(const char *path);
CatProject *cat_load_project_xml_str(const char *src);

/* Информация о том, что было пропущено при загрузке. */
size_t      cat_loader_last_unknown_count(void);
const char *cat_loader_last_unknown_name(size_t i);

#endif
