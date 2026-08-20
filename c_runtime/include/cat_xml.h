/*
 * cat_xml.h - Минималистичный XML-парсер (subset достаточный для code.xml Catrobat).
 * Поддерживает: элементы, атрибуты, текст, CDATA, комментарии, самозакрывающиеся теги.
 * НЕ поддерживает: namespaces, DTD, entity кроме базовых.
 */
#ifndef CAT_XML_H
#define CAT_XML_H

#include <stddef.h>

typedef struct CatXmlAttr {
    char *name;
    char *value;
    struct CatXmlAttr *next;
} CatXmlAttr;

typedef struct CatXmlNode {
    char *tag;
    char *text;   /* конкатенированный текст */
    CatXmlAttr *attrs;
    struct CatXmlNode *parent;
    struct CatXmlNode *first_child;
    struct CatXmlNode *next_sibling;
} CatXmlNode;

/* Парсит XML из строки. Возвращает корневой узел или NULL при ошибке. */
CatXmlNode *cat_xml_parse(const char *src);
CatXmlNode *cat_xml_parse_file(const char *path);
void        cat_xml_free(CatXmlNode *n);

/* Утилиты. */
CatXmlNode *cat_xml_child(CatXmlNode *n, const char *tag);
const char *cat_xml_attr(CatXmlNode *n, const char *name);

#endif
