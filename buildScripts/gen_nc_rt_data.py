#!/usr/bin/env python3
"""Регенерирует c_runtime/src/nc_rt_data.h из c_runtime/include/nc_rt.h.

Встроенная копия мини-рантайма записывается компилятором рядом со
сгенерированным C-кодом. Правится только include/nc_rt.h; этот файл
создаётся скриптом и вручную не редактируется.

Использование:  python3 buildScripts/gen_nc_rt_data.py
"""
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "c_runtime" / "include" / "nc_rt.h"
DST = ROOT / "c_runtime" / "src" / "nc_rt_data.h"

text = SRC.read_text()
if not text.endswith("\n"):
    text += "\n"
lines = text.split("\n")[:-1]  # отбросить последнюю пустую строку после \n

out = []
out.append("/* nc_rt_data.h - nc_rt.h, встроенный в компилятор как строка. */")
out.append("/* Сгенерирован из include/nc_rt.h; не редактируйте вручную. */")
out.append("#ifndef NC_RT_DATA_H")
out.append("#define NC_RT_DATA_H")
out.append("")
out.append("static const char NC_RT_DATA[] =")
for line in lines:
    esc = line.replace("\\", "\\\\").replace('"', '\\"')
    out.append('    "%s\\n"' % esc)
# последнюю строку закрыть точкой с запятой
if out and out[-1].endswith('\\n"'):
    out[-1] = out[-1][:-1] + '";'
out.append("")
out.append("#endif /* NC_RT_DATA_H */")

DST.write_text("\n".join(out) + "\n")
print("wrote %s (%d lines from %s)" % (DST.relative_to(ROOT), len(lines), SRC.relative_to(ROOT)))
