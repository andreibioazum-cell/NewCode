#include "cat_xml.h"
#include "cat_mem.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct { const char *s; size_t pos; } Parser;

static void skip_ws(Parser *p) { while (p->s[p->pos] && isspace((unsigned char)p->s[p->pos])) p->pos++; }

static char *decode_entities(const char *s, size_t len) {
    char *out = (char *)cat_malloc(len + 1);
    size_t w = 0;
    for (size_t i = 0; i < len; ++i) {
        if (s[i] == '&') {
            if (i+3 < len && s[i+1]=='l' && s[i+2]=='t' && s[i+3]==';') { out[w++]='<'; i+=3; }
            else if (i+3 < len && s[i+1]=='g' && s[i+2]=='t' && s[i+3]==';') { out[w++]='>'; i+=3; }
            else if (i+4 < len && s[i+1]=='a' && s[i+2]=='m' && s[i+3]=='p' && s[i+4]==';') { out[w++]='&'; i+=4; }
            else if (i+5 < len && s[i+1]=='q' && s[i+2]=='u' && s[i+3]=='o' && s[i+4]=='t' && s[i+5]==';') { out[w++]='"'; i+=5; }
            else if (i+5 < len && s[i+1]=='a' && s[i+2]=='p' && s[i+3]=='o' && s[i+4]=='s' && s[i+5]==';') { out[w++]='\''; i+=5; }
            else out[w++] = s[i];
        } else out[w++] = s[i];
    }
    out[w] = 0;
    return out;
}

static char *read_name(Parser *p) {
    size_t start = p->pos;
    while (p->s[p->pos] && (isalnum((unsigned char)p->s[p->pos]) || strchr("_-.:", p->s[p->pos])))
        p->pos++;
    if (p->pos == start) return NULL;
    return cat_strndup(p->s + start, p->pos - start);
}

static char *read_quoted(Parser *p) {
    char q = p->s[p->pos];
    if (q != '"' && q != '\'') return NULL;
    p->pos++;
    size_t start = p->pos;
    while (p->s[p->pos] && p->s[p->pos] != q) p->pos++;
    char *raw = cat_strndup(p->s + start, p->pos - start);
    if (p->s[p->pos] == q) p->pos++;
    char *dec = decode_entities(raw, strlen(raw));
    cat_free(raw);
    return dec;
}

static void skip_comment_or_pi(Parser *p) {
    if (p->s[p->pos]=='<' && p->s[p->pos+1]=='!' && p->s[p->pos+2]=='-' && p->s[p->pos+3]=='-') {
        p->pos += 4;
        while (p->s[p->pos] && !(p->s[p->pos]=='-' && p->s[p->pos+1]=='-' && p->s[p->pos+2]=='>')) p->pos++;
        if (p->s[p->pos]) p->pos += 3;
    } else if (p->s[p->pos]=='<' && p->s[p->pos+1]=='?') {
        p->pos += 2;
        while (p->s[p->pos] && !(p->s[p->pos]=='?' && p->s[p->pos+1]=='>')) p->pos++;
        if (p->s[p->pos]) p->pos += 2;
    } else if (p->s[p->pos]=='<' && p->s[p->pos+1]=='!') {
        /* <!DOCTYPE ...> или <![CDATA[...]]> обрабатываем отдельно снаружи */
        while (p->s[p->pos] && p->s[p->pos]!='>') p->pos++;
        if (p->s[p->pos]) p->pos++;
    }
}

static CatXmlNode *new_node(const char *tag) {
    CatXmlNode *n = (CatXmlNode *)cat_calloc(1, sizeof(CatXmlNode));
    n->tag = cat_strdup(tag);
    return n;
}

static void append_child(CatXmlNode *parent, CatXmlNode *child) {
    child->parent = parent;
    if (!parent->first_child) { parent->first_child = child; return; }
    CatXmlNode *c = parent->first_child;
    while (c->next_sibling) c = c->next_sibling;
    c->next_sibling = child;
}

static void append_text(CatXmlNode *n, const char *s, size_t len) {
    if (!len) return;
    size_t old = n->text ? strlen(n->text) : 0;
    n->text = (char *)cat_realloc(n->text, old + len + 1);
    memcpy(n->text + old, s, len);
    n->text[old + len] = 0;
}

static CatXmlNode *parse_element(Parser *p);

static void parse_children(Parser *p, CatXmlNode *node) {
    while (p->s[p->pos]) {
        if (p->s[p->pos] == '<') {
            if (p->s[p->pos+1] == '/') return; /* конец */
            if (p->s[p->pos+1] == '!' && p->s[p->pos+2] == '[' &&
                strncmp(p->s + p->pos, "<![CDATA[", 9) == 0) {
                p->pos += 9;
                size_t start = p->pos;
                while (p->s[p->pos] && !(p->s[p->pos]==']' && p->s[p->pos+1]==']' && p->s[p->pos+2]=='>')) p->pos++;
                append_text(node, p->s + start, p->pos - start);
                if (p->s[p->pos]) p->pos += 3;
                continue;
            }
            if (p->s[p->pos+1] == '!' || p->s[p->pos+1] == '?') { skip_comment_or_pi(p); continue; }
            CatXmlNode *ch = parse_element(p);
            if (!ch) return;
            append_child(node, ch);
        } else {
            size_t start = p->pos;
            while (p->s[p->pos] && p->s[p->pos] != '<') p->pos++;
            char *dec = decode_entities(p->s + start, p->pos - start);
            /* добавляем только непустой текст */
            const char *t = dec; while (*t && isspace((unsigned char)*t)) t++;
            if (*t) append_text(node, dec, strlen(dec));
            cat_free(dec);
        }
    }
}

static CatXmlNode *parse_element(Parser *p) {
    if (p->s[p->pos] != '<') return NULL;
    p->pos++;
    char *name = read_name(p);
    if (!name) return NULL;
    CatXmlNode *n = new_node(name);
    cat_free(name);
    /* атрибуты */
    while (1) {
        skip_ws(p);
        char c = p->s[p->pos];
        if (c == '/' || c == '>' || c == 0) break;
        char *an = read_name(p);
        if (!an) break;
        skip_ws(p);
        char *av = NULL;
        if (p->s[p->pos] == '=') { p->pos++; skip_ws(p); av = read_quoted(p); }
        CatXmlAttr *a = (CatXmlAttr *)cat_calloc(1, sizeof(CatXmlAttr));
        a->name = an; a->value = av ? av : cat_strdup("");
        a->next = n->attrs; n->attrs = a;
    }
    if (p->s[p->pos] == '/') { p->pos++; if (p->s[p->pos]=='>') p->pos++; return n; }
    if (p->s[p->pos] == '>') p->pos++;
    parse_children(p, n);
    /* закрытие */
    if (p->s[p->pos] == '<' && p->s[p->pos+1] == '/') {
        p->pos += 2;
        char *cn = read_name(p);
        cat_free(cn);
        while (p->s[p->pos] && p->s[p->pos] != '>') p->pos++;
        if (p->s[p->pos]) p->pos++;
    }
    return n;
}

CatXmlNode *cat_xml_parse(const char *src) {
    if (!src) return NULL;
    Parser p = { src, 0 };
    skip_ws(&p);
    while (p.s[p.pos] == '<' && (p.s[p.pos+1] == '?' || p.s[p.pos+1] == '!')) {
        skip_comment_or_pi(&p); skip_ws(&p);
    }
    return parse_element(&p);
}

CatXmlNode *cat_xml_parse_file(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
    if (sz < 0) { fclose(f); return NULL; }
    char *buf = (char *)cat_malloc((size_t)sz + 1);
    size_t rd = fread(buf, 1, (size_t)sz, f);
    buf[rd] = 0;
    fclose(f);
    CatXmlNode *n = cat_xml_parse(buf);
    cat_free(buf);
    return n;
}

void cat_xml_free(CatXmlNode *n) {
    if (!n) return;
    CatXmlNode *c = n->first_child;
    while (c) { CatXmlNode *nx = c->next_sibling; cat_xml_free(c); c = nx; }
    CatXmlAttr *a = n->attrs;
    while (a) { CatXmlAttr *nx = a->next; cat_free(a->name); cat_free(a->value); cat_free(a); a = nx; }
    cat_free(n->tag); cat_free(n->text); cat_free(n);
}

CatXmlNode *cat_xml_child(CatXmlNode *n, const char *tag) {
    if (!n) return NULL;
    for (CatXmlNode *c = n->first_child; c; c = c->next_sibling)
        if (strcmp(c->tag, tag) == 0) return c;
    return NULL;
}

const char *cat_xml_attr(CatXmlNode *n, const char *name) {
    if (!n) return NULL;
    for (CatXmlAttr *a = n->attrs; a; a = a->next)
        if (strcmp(a->name, name) == 0) return a->value;
    return NULL;
}
