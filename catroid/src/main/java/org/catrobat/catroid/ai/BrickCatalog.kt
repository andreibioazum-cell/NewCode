/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2026 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.ai

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Каталог всех блоков NewCode. На устройстве читает JSON из assets
 * (генерируется при сборке из исходников репозитория), поэтому помощник
 * «видит» буквально все блоки проекта, включая редкие.
 */
object BrickCatalog {

    private const val ASSET_FILE = "brick_catalog.json"

    data class BrickSpec(
        val className: String,
        val constructors: List<String>,
        val setters: List<String>
    )

    private var cached: List<BrickSpec>? = null

    fun allBricks(context: Context): List<BrickSpec> {
        cached?.let { return it }
        val parsed = runCatching {
            val text = context.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
            parseCatalogJson(text)
        }.getOrElse {
            // Fallback: если assets нет (например, в юнит-тестах) — читаем исходники.
            parseFromSourceDir()
        }
        cached = parsed
        return parsed
    }

    fun parseCatalogJson(text: String): List<BrickSpec> = runCatching {
        val root = JsonParser.parseString(text).asJsonObject
        val result = mutableListOf<BrickSpec>()
        root.getAsJsonArray("bricks").forEach { entry ->
            val obj = entry.asJsonObject
            val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
            val ctors = obj.getAsJsonArray("constructors").mapNotNull { it.takeIf { e -> !e.isJsonNull }?.asString }
            val setters = obj.getAsJsonArray("setters").mapNotNull { it.takeIf { e -> !e.isJsonNull }?.asString }
            result.add(BrickSpec(name, ctors, setters))
        }
        result
    }.getOrDefault(emptyList())

    private fun parseFromSourceDir(): List<BrickSpec> {
        val dir = File("catroid/src/main/java/org/catrobat/catroid/content/bricks")
        if (!dir.isDirectory) {
            return emptyList()
        }
        val result = mutableListOf<BrickSpec>()
        dir.listFiles { f -> f.isFile && (f.name.endsWith(".java") || f.name.endsWith(".kt")) }
            ?.sortedBy { it.name.lowercase() }
            ?.forEach { file ->
                val name = file.name.removeSuffix(if (file.name.endsWith(".kt")) ".kt" else ".java")
                if (!name.endsWith("Brick")) return@forEach
                val text = file.readText()
                val ctorPattern = Regex("(?:public\\s+)?(?:$name)\\s*\\(([^)]*)\\)")
                val ctors = ctorPattern.findAll(text).mapNotNull { m ->
                    val p = m.groupValues[1].trim()
                    if (p.isEmpty()) null else p.replace(Regex("@\\w+"), "").replace(Regex("\\s+"), " ").trim()
                }.toList()
                val setterPattern = Regex("public\\s+void\\s+set(\\w+)\\s*\\(([^)]*)\\)")
                val setters = setterPattern.findAll(text).mapNotNull { m ->
                    val p = m.groupValues[2].trim()
                    if (p.isEmpty()) null else "${m.groupValues[1]}: ${p.replace(Regex("\\s+"), " ").trim()}"
                }.toList()
                result.add(BrickSpec(name, ctors, setters))
            }
        return result
    }

    /** JSON-описание одного блока (компактное, для промпта). */
    fun toJson(spec: BrickSpec): JsonObject = JsonObject().apply {
        addProperty("name", spec.className)
        val ctorArray = JsonArray()
        spec.constructors.forEach { ctorArray.add(it) }
        add("constructors", ctorArray)
        val setterArray = JsonArray()
        spec.setters.forEach { setterArray.add(it) }
        add("setters", setterArray)
    }

    /** Короткое человекочитаемое описание блока для лога/подсказки. */
    fun describe(context: Context, className: String): String {
        val spec = allBricks(context).firstOrNull { it.className == className } ?: return className
        val parts = mutableListOf(className)
        if (spec.constructors.isNotEmpty()) {
            parts.add("ctors: " + spec.constructors.joinToString(" | ") { "($it)" })
        }
        if (spec.setters.isNotEmpty()) {
            parts.add("setters: " + spec.setters.joinToString(", "))
        }
        return parts.joinToString(" ")
    }
}
