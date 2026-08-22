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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.catrobat.catroid.common.Constants
import org.catrobat.catroid.common.LookData
import org.catrobat.catroid.common.SoundInfo
import org.catrobat.catroid.common.ThreeDLookGenerator
import org.catrobat.catroid.common.ThreeDShape
import org.catrobat.catroid.content.BroadcastScript
import org.catrobat.catroid.content.Project
import org.catrobat.catroid.content.Scene
import org.catrobat.catroid.content.Script
import org.catrobat.catroid.content.Sprite
import org.catrobat.catroid.content.StartScript
import org.catrobat.catroid.content.WhenBackgroundChangesScript
import org.catrobat.catroid.content.WhenScript
import org.catrobat.catroid.content.bricks.Brick
import org.catrobat.catroid.content.bricks.BroadcastBrick
import org.catrobat.catroid.content.bricks.BroadcastWaitBrick
import org.catrobat.catroid.content.bricks.ChangeSizeByNBrick
import org.catrobat.catroid.content.bricks.ForeverBrick
import org.catrobat.catroid.content.bricks.GlideToBrick
import org.catrobat.catroid.content.bricks.GoToBrick
import org.catrobat.catroid.content.bricks.HideBrick
import org.catrobat.catroid.content.bricks.MoveNStepsBrick
import org.catrobat.catroid.content.bricks.PlaceAtBrick
import org.catrobat.catroid.content.bricks.PlaySoundBrick
import org.catrobat.catroid.content.bricks.PointInDirectionBrick
import org.catrobat.catroid.content.bricks.SayBubbleBrick
import org.catrobat.catroid.content.bricks.SayForBubbleBrick
import org.catrobat.catroid.content.bricks.SetLookBrick
import org.catrobat.catroid.content.bricks.SetSizeToBrick
import org.catrobat.catroid.content.bricks.SetVolumeToBrick
import org.catrobat.catroid.content.bricks.ShowBrick
import org.catrobat.catroid.content.bricks.ThinkBubbleBrick
import org.catrobat.catroid.content.bricks.ThinkForBubbleBrick
import org.catrobat.catroid.content.bricks.TurnLeftBrick
import org.catrobat.catroid.content.bricks.TurnRightBrick
import org.catrobat.catroid.content.bricks.WaitBrick
import org.catrobat.catroid.content.bricks.UserListBrick
import org.catrobat.catroid.formulaeditor.Formula
import org.catrobat.catroid.formulaeditor.UserList
import org.catrobat.catroid.formulaeditor.UserVariable
import org.catrobat.catroid.io.StorageOperations
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Modifier

/**
 * Движок действий ИИ-помощника: выполняет вызовы функций модели
 * (function calling) и напрямую изменяет открытый проект NewCode —
 * спрайты, образы, звуки, скрипты и блоки.
 */
object AiProjectActions {

    private const val DEFAULT_COLOR = "#4CAF50"
    private const val LOOK_SIZE = 256

    val toolsJson: JsonArray by lazy { buildTools() }

    fun execute(
        context: Context,
        project: Project,
        scene: Scene,
        action: String,
        argumentsJson: String
    ): String {
        return try {
            val args = runCatching { JsonParser.parseString(argumentsJson).asJsonObject }
                .getOrElse { JsonObject() }
            val result: JsonObject = when (action) {
                "add_sprite" -> addSprite(scene, args)
                "rename_sprite" -> renameSprite(scene, args)
                "delete_sprite" -> deleteSprite(scene, args)
                "add_look" -> addLook(scene, args)
                "rename_look" -> renameLook(scene, args)
                "delete_look" -> deleteLook(scene, args)
                "set_sprite_look" -> setSpriteLook(scene, args)
                "change_background" -> changeBackground(scene, args)
                "add_sound_to_sprite" -> addSoundToSprite(scene, args)
                "rename_sound" -> renameSound(scene, args)
                "delete_sound" -> deleteSound(scene, args)
                "add_script" -> addScript(scene, args)
                "delete_script" -> deleteScript(scene, args)
                "clear_script" -> clearScript(scene, args)
                "add_brick" -> addBrick(scene, args)
                "add_brick_to_loop" -> addBrickToLoop(scene, args)
                "delete_brick" -> deleteBrick(scene, args)
                "add_any_brick" -> addAnyBrick(context, scene, args)
                "create_3d_object" -> create3dObject(scene, args)
                "add_variable" -> addVariable(scene, args)
                "set_variable_value" -> setVariableValue(scene, args)
                "add_list" -> addList(scene, args)
                "search_bricks" -> searchBricks(context, args)
                "get_project_state" -> getProjectState(project)
                "set_project_name" -> setProjectName(project, args)
                else -> error("Неизвестное действие: $action")
            }
            result.toString()
        } catch (e: Exception) {
            JsonObject().apply {
                addProperty("ok", false)
                addProperty("error", e.message ?: e.javaClass.simpleName)
            }.toString()
        }
    }

    // ------------------------------------------------------------------
    // Действия со спрайтами
    // ------------------------------------------------------------------

    private fun addSprite(scene: Scene, args: JsonObject): JsonObject {
        val name = str(args, "name").trim()
        if (name.isEmpty()) {
            return errorResult("Укажите имя спрайта")
        }
        if (scene.getSprite(name) != null) {
            return errorResult("Спрайт «$name» уже существует")
        }
        val sprite = Sprite(name)
        scene.addSprite(sprite)
        val colorHex = str(args, "color", "").trim()
        if (colorHex.isNotEmpty()) {
            val lookResult = addColoredLook(scene, sprite, name, colorHex)
            if (!lookResult.get("ok").asBoolean) {
                return lookResult
            }
        }
        return okResult("Спрайт «$name» создан")
    }

    private fun renameSprite(scene: Scene, args: JsonObject): JsonObject {
        val name = str(args, "name").trim()
        val newName = str(args, "new_name").trim()
        if (newName.isEmpty()) {
            return errorResult("Укажите новое имя")
        }
        val sprite = findSprite(scene, name) ?: return errorResult("Спрайт «$name» не найден")
        if (scene.getSprite(newName) != null) {
            return errorResult("Спрайт «$newName» уже существует")
        }
        sprite.setName(newName)
        return okResult("Спрайт «$name» переименован в «$newName»")
    }

    private fun deleteSprite(scene: Scene, args: JsonObject): JsonObject {
        val name = str(args, "name").trim()
        val sprite = findSprite(scene, name) ?: return errorResult("Спрайт «$name» не найден")
        if (sprite === scene.getBackgroundSprite()) {
            return errorResult("Нельзя удалить спрайт-фон сцены")
        }
        scene.removeSprite(sprite)
        return okResult("Спрайт «$name» удалён")
    }

    // ------------------------------------------------------------------
    // Действия с образами (внешний вид)
    // ------------------------------------------------------------------

    private fun addLook(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт «${str(args, "sprite_name")}» не найден")
        val lookName = str(args, "look_name").trim()
        if (lookName.isEmpty()) {
            return errorResult("Укажите имя образа")
        }
        return addColoredLook(scene, sprite, lookName, str(args, "color", DEFAULT_COLOR))
    }

    private fun getProjectState(project: Project): JsonObject = okResult(
        "Актуальное состояние проекта"
    ) {
        addProperty("state", ProjectSnapshot.build(project))
    }

    private fun create3dObject(scene: Scene, args: JsonObject): JsonObject {
        val spriteName = str(args, "sprite_name").trim()
        val sprite = findSprite(scene, spriteName)
            ?: return errorResult("Спрайт «$spriteName» не найден")
        val shapeName = str(args, "shape").trim().lowercase()
        val shape = when (shapeName) {
            "cube", "куб" -> ThreeDShape.CUBE
            "sphere", "сфера", "шар" -> ThreeDShape.SPHERE
            "pyramid", "пирамида" -> ThreeDShape.PYRAMID
            "cylinder", "цилиндр" -> ThreeDShape.CYLINDER
            else -> return errorResult("Неизвестная фигура «$shapeName». Доступны: cube, sphere, pyramid, cylinder")
        }
        val size = dbl(args, "size", 100.0)
        val color = str(args, "color", "#4CAF50")
        val lookName = ThreeDLookGenerator.createLook(scene, sprite, shape, size.toInt(), color)
            ?: return errorResult("Не удалось создать 3D-фигуру (ошибка записи образа)")
        return okResult("Создан 3D-образ «$lookName» (${shapeName}) для спрайта «$spriteName»")
    }

    private fun renameLook(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт не найден")
        val look = sprite.lookList.firstOrNull { it.name == str(args, "look_name") }
            ?: return errorResult("Образ «${str(args, "look_name")}» не найден")
        val newName = str(args, "new_name").trim()
        if (newName.isEmpty()) {
            return errorResult("Укажите новое имя образа")
        }
        look.setName(newName)
        return okResult("Образ переименован в «$newName»")
    }

    private fun deleteLook(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт не найден")
        val look = sprite.lookList.firstOrNull { it.name == str(args, "look_name") }
            ?: return errorResult("Образ «${str(args, "look_name")}» не найден")
        sprite.lookList.remove(look)
        return okResult("Образ «${look.name}» удалён у спрайта «${sprite.name}»")
    }

    private fun setSpriteLook(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт не найден")
        val look = sprite.lookList.firstOrNull { it.name == str(args, "look_name") }
            ?: return errorResult("Образ «${str(args, "look_name")}» не найден")
        sprite.lookList.remove(look)
        sprite.lookList.add(0, look)
        sprite.look.setLookData(look)
        return okResult("У спрайта «${sprite.name}» активный образ — «${look.name}»")
    }

    private fun changeBackground(scene: Scene, args: JsonObject): JsonObject {
        val background = scene.getBackgroundSprite()
            ?: return errorResult("В сцене нет спрайта-фона")
        val lookName = str(args, "look_name", "Фон").trim().ifEmpty { "Фон" }
        val lookResult = addColoredLook(scene, background, lookName, str(args, "color", DEFAULT_COLOR))
        if (!lookResult.get("ok").asBoolean) {
            return lookResult
        }
        val look = background.lookList.lastOrNull() ?: return errorResult("Не удалось создать фон")
        background.lookList.remove(look)
        background.lookList.add(0, look)
        return okResult("Фон сцены изменён на «$lookName»")
    }

    // ------------------------------------------------------------------
    // Действия со звуками
    // ------------------------------------------------------------------

    private fun addSoundToSprite(scene: Scene, args: JsonObject): JsonObject {
        val target = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт не найден")
        val source = findSprite(scene, str(args, "source_sprite_name").trim())
            ?: return errorResult("Спрайт-источник не найден")
        val sound = source.soundList.firstOrNull { it.name == str(args, "source_sound_name") }
            ?: return errorResult("Звук «${str(args, "source_sound_name")}» не найден")
        val newName = str(args, "new_sound_name").trim().ifEmpty { sound.name }
        val soundsDir = File(scene.directory, Constants.SOUND_DIRECTORY_NAME)
        if (!soundsDir.exists() && !soundsDir.mkdirs()) {
            return errorResult("Не удалось создать каталог звуков")
        }
        val copiedFile = StorageOperations.copyFileToDir(sound.file, soundsDir)
        val soundInfo = SoundInfo(newName, copiedFile)
        target.soundList.add(soundInfo)
        return okResult("Звук «$newName» добавлен спрайту «${target.name}»")
    }

    private fun renameSound(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт не найден")
        val sound = sprite.soundList.firstOrNull { it.name == str(args, "sound_name") }
            ?: return errorResult("Звук «${str(args, "sound_name")}» не найден")
        val newName = str(args, "new_name").trim()
        if (newName.isEmpty()) {
            return errorResult("Укажите новое имя звука")
        }
        sound.setName(newName)
        return okResult("Звук переименован в «$newName»")
    }

    private fun deleteSound(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт не найден")
        val sound = sprite.soundList.firstOrNull { it.name == str(args, "sound_name") }
            ?: return errorResult("Звук «${str(args, "sound_name")}» не найден")
        sprite.soundList.remove(sound)
        return okResult("Звук «${sound.name}» удалён")
    }

    // ------------------------------------------------------------------
    // Действия со скриптами
    // ------------------------------------------------------------------

    private fun addScript(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт «${str(args, "sprite_name")}» не найден")
        val script: Script = when (str(args, "script_type")) {
            "start" -> StartScript()
            "tapped" -> WhenScript()
            "receive" -> {
                val message = str(args, "message").trim()
                if (message.isEmpty()) {
                    return errorResult("Для скрипта receive укажите сообщение (message)")
                }
                BroadcastScript(message)
            }
            "background_changes" -> WhenBackgroundChangesScript()
            else -> return errorResult("Неизвестный тип скрипта (start|tapped|receive|background_changes)")
        }
        sprite.addScript(script)
        val index = sprite.scriptList.indexOf(script)
        return okResult("Скрипт добавлен спрайту «${sprite.name}»") {
            addProperty("script_index", index)
        }
    }

    private fun deleteScript(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт не найден")
        val index = int(args, "script_index", 0)
        val script = sprite.scriptList.getOrNull(index)
            ?: return errorResult("Скрипт с индексом $index не найден")
        sprite.scriptList.remove(script)
        return okResult("Скрипт с индексом $index удалён")
    }

    private fun clearScript(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт не найден")
        val index = int(args, "script_index", 0)
        val script = sprite.scriptList.getOrNull(index)
            ?: return errorResult("Скрипт с индексом $index не найден")
        script.brickList.clear()
        return okResult("Скрипт с индексом $index очищен от блоков")
    }

    // ------------------------------------------------------------------
    // Действия с блоками
    // ------------------------------------------------------------------

    private fun addBrick(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт «${str(args, "sprite_name")}» не найден")
        val scriptIndex = int(args, "script_index", 0)
        val script = sprite.scriptList.getOrNull(scriptIndex)
            ?: return errorResult("Скрипт с индексом $scriptIndex не найден")
        val brick = buildBrick(scene, sprite, args)
        val positionArg = args.get("position")
        val position = positionArg?.takeIf { !it.isJsonNull }?.asInt
        if (position != null && position in 0..script.brickList.size) {
            script.addBrick(position, brick)
        } else {
            script.addBrick(brick)
        }
        val brickIndex = script.brickList.indexOf(brick)
        return okResult("Блок ${brickTypeName(str(args, "brick_type"))} добавлен") {
            addProperty("brick_index", brickIndex)
            if (brick is ForeverBrick) {
                addProperty("loop_brick_index", brickIndex)
            }
        }
    }

    private fun addBrickToLoop(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт «${str(args, "sprite_name")}» не найден")
        val scriptIndex = int(args, "script_index", 0)
        val script = sprite.scriptList.getOrNull(scriptIndex)
            ?: return errorResult("Скрипт с индексом $scriptIndex не найден")
        val loopBrickIndex = int(args, "loop_brick_index", 0)
        val loopBrick = script.brickList.getOrNull(loopBrickIndex)
            ?: return errorResult("Блок с индексом $loopBrickIndex не найден")
        if (loopBrick !is ForeverBrick) {
            return errorResult("Блок с индексом $loopBrickIndex не является циклом forever")
        }
        val innerBrick = buildBrick(scene, sprite, args)
        loopBrick.addBrick(innerBrick)
        return okResult("Блок ${brickTypeName(str(args, "brick_type"))} добавлен внутрь цикла")
    }

    private fun deleteBrick(scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт не найден")
        val scriptIndex = int(args, "script_index", 0)
        val script = sprite.scriptList.getOrNull(scriptIndex)
            ?: return errorResult("Скрипт с индексом $scriptIndex не найден")
        val brickIndex = int(args, "brick_index", 0)
        val brick = script.brickList.getOrNull(brickIndex)
            ?: return errorResult("Блок с индексом $brickIndex не найден")
        script.removeBrick(brick)
        return okResult("Блок с индексом $brickIndex удалён")
    }

    // ------------------------------------------------------------------
    // Универсальный блок: любой класс блока из репозитория через рефлексию
    // ------------------------------------------------------------------

    private fun addAnyBrick(context: Context, scene: Scene, args: JsonObject): JsonObject {
        val sprite = findSprite(scene, str(args, "sprite_name").trim())
            ?: return errorResult("Спрайт «${str(args, "sprite_name")}» не найден")
        val scriptIndex = int(args, "script_index", 0)
        val script = sprite.scriptList.getOrNull(scriptIndex)
            ?: return errorResult("Скрипт с индексом $scriptIndex не найден")
        val brickClassName = str(args, "brick_class").trim()
        if (brickClassName.isEmpty()) {
            return errorResult("Укажите brick_class — имя класса блока, например SetVariableBrick")
        }
        val brick = reflectBrick(context, brickClassName, scene, sprite, args)
            ?: return errorResult(
                "Не удалось создать блок $brickClassName. " +
                    "Используйте search_bricks, чтобы найти точное имя класса, или add_brick для стандартных блоков."
            )
        val position = args.get("position")?.takeIf { !it.isJsonNull }?.asInt
        if (position != null && position in 0..script.brickList.size) {
            script.addBrick(position, brick)
        } else {
            script.addBrick(brick)
        }
        val brickIndex = script.brickList.indexOf(brick)
        return okResult("Блок $brickClassName добавлен") {
            addProperty("brick_index", brickIndex)
            if (brick is ForeverBrick) {
                addProperty("loop_brick_index", brickIndex)
            }
        }
    }

    /**
     * Создаёт экземпляр любого класса блока репозитория.
     * Параметры берутся из массива params: [{name, value}] (см. add_any_brick).
     * Пытается: 1) конструктор, чьи параметры можно заполнить;
     * 2) конструктор без параметров + сеттеры.
     */
    private fun reflectBrick(
        context: Context,
        className: String,
        scene: Scene,
        sprite: Sprite,
        args: JsonObject
    ): Brick? {
        val paramsMap = parseParams(args)
        val fullName = if (className.contains('.')) className else "org.catrobat.catroid.content.bricks.$className"
        val clazz = runCatching { Class.forName(fullName) }.getOrNull()
            ?: return null
        if (!Brick::class.java.isAssignableFrom(clazz)) {
            return null
        }
        val availableConstructors = clazz.constructors
            .filter { !Modifier.isAbstract(clazz.modifiers) }
            .sortedBy { it.parameterCount }

        // 1) Пытаемся найти конструктор, заполнимый из paramsMap.
        //    Для конструктора с N параметрами пробуем сопоставить их
        //    с N значениями paramsMap (по порядку следования).
        val paramValues = paramsMap.values.toList()
        for (constructor in availableConstructors) {
            val paramTypes = constructor.parameterTypes
            if (paramTypes.size == 1) {
                val value = convertArg(paramsMap, paramTypes[0], scene, sprite)
                if (value != null) {
                    return runCatching {
                        constructor.newInstance(value) as Brick
                    }.getOrNull()
                }
            } else if (paramTypes.size == paramValues.size) {
                // Сначала пробуем сопоставить по «говорящим» именам типов
                // (например, Formula + UserVariable), если ключи в paramsMap
                // называются так же, как параметр.
                val values = paramTypes.mapIndexed { index, paramType ->
                    val keyCandidate = paramType.simpleName.replaceFirstChar { it.lowercaseChar() }
                    val element = paramsMap[keyCandidate] ?: paramValues[index]
                    convertValue(element, paramType, scene, sprite)
                }
                if (values.all { it != null }) {
                    return runCatching {
                        constructor.newInstance(*values.toTypedArray()) as Brick
                    }.getOrNull()
                }
                // Если не вышло — по порядку.
                val valuesOrdered = paramTypes.mapIndexed { index, paramType ->
                    convertValue(paramValues[index], paramType, scene, sprite)
                }
                if (valuesOrdered.all { it != null }) {
                    return runCatching {
                        constructor.newInstance(*valuesOrdered.toTypedArray()) as Brick
                    }.getOrNull()
                }
            }
        }

        // 2) Конструктор без параметров + сеттеры.
        val noArgCtor: Constructor<*>? = availableConstructors.firstOrNull { it.parameterCount == 0 }
        if (noArgCtor == null) {
            return null
        }
        val brick = runCatching { noArgCtor.newInstance() as Brick }.getOrNull() ?: return null
        var applied = 0
        clazz.methods.forEach { method ->
            if (method.name.startsWith("set") && method.parameterCount == 1) {
                val setterName = method.name.removePrefix("set")
                val argName = setterName.replaceFirstChar { it.lowercaseChar() }
                if (paramsMap.containsKey(argName)) {
                    val value = convertArg(paramsMap, method.parameterTypes[0], scene, sprite)
                    if (value != null) {
                        runCatching { method.invoke(brick, value) }.onSuccess { applied++ }
                    }
                }
            }
        }
        if (applied == 0 && brick !is Brick) {
            return null
        }
        return brick
    }

    /** Преобразует массив params [{name,value}] в карту name -> JsonElement. */
    private fun parseParams(args: JsonObject): Map<String, com.google.gson.JsonElement> {
        val result = mutableMapOf<String, com.google.gson.JsonElement>()
        val params = args.get("params")?.takeIf { !it.isJsonNull }?.asJsonArray
        params?.forEach { item ->
            val obj = item.asJsonObject
            val name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
            val value = obj.get("value")?.takeIf { !it.isJsonNull } ?: return@forEach
            result[name] = value
        }
        // Также поддерживаем прямое указание: {name: value} на верхнем уровне.
        args.keySet().forEach { key ->
            if (key != "params" && key != "sprite_name" && key != "script_index" &&
                key != "brick_class" && key != "position"
            ) {
                if (!result.containsKey(key)) {
                    result[key] = args.get(key)
                }
            }
        }
        return result
    }

    /** Преобразует значение из карты params в значение нужного типа (для рефлексии). */
    private fun convertArg(
        paramsMap: Map<String, com.google.gson.JsonElement>,
        type: Class<*>,
        scene: Scene,
        sprite: Sprite
    ): Any? {
        val element = paramsMap.values.firstOrNull { it != null } ?: return null
        return convertValue(element, type, scene, sprite)
    }

    /** Преобразует конкретный JsonElement в значение нужного типа. */
    private fun convertValue(
        element: com.google.gson.JsonElement,
        type: Class<*>,
        scene: Scene,
        sprite: Sprite
    ): Any? {
        val name = type.simpleName
        return when (name) {
            "int", "Integer" -> element.asInt
            "double", "Double" -> element.asDouble
            "float", "Float" -> element.asFloat
            "long", "Long" -> element.asLong
            "boolean", "Boolean" -> element.asBoolean
            "String" -> element.asString
            "Formula" -> runCatching { Formula(element.asString) }.getOrNull()
            "UserVariable" -> element.asString.let { nameArg ->
                sprite.getUserVariables().firstOrNull { it.name == nameArg }
                    ?: scene.project?.getUserVariables()?.firstOrNull { it.name == nameArg }
            }
            "UserList" -> element.asString.let { nameArg ->
                sprite.getUserLists().firstOrNull { it.name == nameArg }
                    ?: scene.project?.getUserLists()?.firstOrNull { it.name == nameArg }
            }
            "Sprite" -> element.asString.let { scene.getSprite(it) }
            "LookData" -> element.asString.let { nameArg ->
                sprite.lookList.firstOrNull { it.name == nameArg }
            }
            "SoundInfo" -> element.asString.let { nameArg ->
                sprite.soundList.firstOrNull { it.name == nameArg }
            }
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // Переменные и списки
    // ------------------------------------------------------------------

    private fun addVariable(scene: Scene, args: JsonObject): JsonObject {
        val name = str(args, "name").trim()
        if (name.isEmpty()) {
            return errorResult("Укажите имя переменной")
        }
        val variable = UserVariable(name)
        if (args.get("value")?.takeIf { !it.isJsonNull } != null) {
            variable.setValue(args.get("value").asString)
        }
        val scope = str(args, "scope", "project")
        if (scope == "sprite") {
            val sprite = findSprite(scene, str(args, "sprite_name").trim())
                ?: return errorResult("Спрайт не найден")
            sprite.addUserVariable(variable)
        } else {
            scene.project?.addUserVariable(variable)
        }
        return okResult("Переменная «$name» создана (${if (scope == "sprite") "спрайт" else "проект"})")
    }

    private fun setVariableValue(scene: Scene, args: JsonObject): JsonObject {
        val name = str(args, "name").trim()
        val variable = findVariable(scene, name)
            ?: return errorResult("Переменная «$name» не найдена. Используйте add_variable")
        variable.setValue(str(args, "value"))
        return okResult("Переменная «$name» = «${str(args, "value")}»")
    }

    private fun addList(scene: Scene, args: JsonObject): JsonObject {
        val name = str(args, "name").trim()
        if (name.isEmpty()) {
            return errorResult("Укажите имя списка")
        }
        val list = UserList(name)
        if (args.get("value")?.takeIf { !it.isJsonNull }?.isJsonArray == true) {
            val values = mutableListOf<Any>()
            args.get("value").asJsonArray.forEach { values.add(it.asString) }
            list.setValue(values)
        }
        val scope = str(args, "scope", "project")
        if (scope == "sprite") {
            val sprite = findSprite(scene, str(args, "sprite_name").trim())
                ?: return errorResult("Спрайт не найден")
            sprite.addUserList(list)
        } else {
            scene.project?.addUserList(list)
        }
        return okResult("Список «$name» создан")
    }

    private fun findVariable(scene: Scene, name: String): UserVariable? {
        scene.spriteList.forEach { sprite ->
            sprite.getUserVariables().firstOrNull { it.name == name }?.let { return it }
        }
        return scene.project?.getUserVariables()?.firstOrNull { it.name == name }
    }

    private fun searchBricks(context: Context, args: JsonObject): JsonObject {
        val query = str(args, "query").trim().lowercase()
        val catalog = BrickCatalog.allBricks(context)
        val matches = if (query.isEmpty()) {
            catalog
        } else {
            catalog.filter {
                it.className.lowercase().contains(query) ||
                    it.constructors.any { c -> c.lowercase().contains(query) }
            }
        }.take(20)
        val result = JsonObject()
        result.addProperty("ok", true)
        val summary = "Найдено блоков: ${matches.size}"
        result.addProperty("summary", summary)
        val bricks = JsonArray()
        matches.forEach { spec -> bricks.add(BrickCatalog.toJson(spec)) }
        result.add("bricks", bricks)
        return result
    }

    private fun setProjectName(project: Project, args: JsonObject): JsonObject {
        val name = str(args, "name").trim()
        if (name.isEmpty()) {
            return errorResult("Укажите имя проекта")
        }
        project.setName(name)
        return okResult("Имя проекта изменено на «$name»")
    }

    // ------------------------------------------------------------------
    // Построение блоков
    // ------------------------------------------------------------------

    private fun buildBrick(scene: Scene, sprite: Sprite, args: JsonObject): Brick {
        return when (str(args, "brick_type")) {
            "move_n_steps" -> MoveNStepsBrick(dbl(args, "steps", 10.0))
            "turn_left" -> TurnLeftBrick(dbl(args, "degrees", 15.0))
            "turn_right" -> TurnRightBrick(dbl(args, "degrees", 15.0))
            "go_to_xy" -> PlaceAtBrick(int(args, "x", 0), int(args, "y", 0))
            "go_to_sprite" -> {
                val target = findSprite(scene, str(args, "target_sprite").trim())
                    ?: throw IllegalArgumentException("Спрайт «${str(args, "target_sprite")}» не найден")
                GoToBrick(target)
            }
            "point_in_direction" -> PointInDirectionBrick(dbl(args, "degrees", 90.0))
            "glide_to" -> GlideToBrick(
                Formula(int(args, "x", 0)),
                Formula(int(args, "y", 0)),
                Formula(dbl(args, "seconds", 1.0))
            )
            "say" -> SayBubbleBrick(str(args, "text", ""))
            "say_for" -> SayForBubbleBrick(str(args, "text", ""), dbl(args, "seconds", 2.0).toFloat())
            "think" -> ThinkBubbleBrick(str(args, "text", ""))
            "think_for" -> ThinkForBubbleBrick(str(args, "text", ""), dbl(args, "seconds", 2.0).toFloat())
            "show" -> ShowBrick()
            "hide" -> HideBrick()
            "set_look" -> {
                val look = sprite.lookList.firstOrNull { it.name == str(args, "look_name") }
                    ?: throw IllegalArgumentException("Образ «${str(args, "look_name")}» не найден у спрайта «${sprite.name}»")
                SetLookBrick().apply { setLook(look) }
            }
            "set_size_to" -> SetSizeToBrick(dbl(args, "percent", 100.0))
            "change_size_by" -> ChangeSizeByNBrick(dbl(args, "percent", 10.0))
            "play_sound" -> {
                val sound = sprite.soundList.firstOrNull { it.name == str(args, "sound_name") }
                    ?: throw IllegalArgumentException("Звук «${str(args, "sound_name")}» не найден у спрайта «${sprite.name}»")
                PlaySoundBrick().apply { setSound(sound) }
            }
            "set_volume_to" -> SetVolumeToBrick(dbl(args, "volume", 50.0))
            "wait" -> WaitBrick((dbl(args, "seconds", 1.0) * 1000).toInt())
            "broadcast" -> BroadcastBrick(str(args, "message", ""))
            "broadcast_wait" -> BroadcastWaitBrick(str(args, "message", ""))
            "forever" -> ForeverBrick()
            else -> throw IllegalArgumentException(
                "Неизвестный тип блока: ${str(args, "brick_type")}"
            )
        }
    }

    private fun brickTypeName(brickType: String): String = when (brickType) {
        "move_n_steps" -> "«двигаться на N шагов»"
        "turn_left" -> "«повернуть налево»"
        "turn_right" -> "«повернуть направо»"
        "go_to_xy" -> "«перейти в X: Y:»"
        "go_to_sprite" -> "«перейти к спрайту»"
        "point_in_direction" -> "«повернуться в направлении»"
        "glide_to" -> "«плавно переместиться»"
        "say" -> "«сказать»"
        "say_for" -> "«сказать N секунд»"
        "think" -> "«подумать»"
        "think_for" -> "«подумать N секунд»"
        "show" -> "«показаться»"
        "hide" -> "«спрятаться»"
        "set_look" -> "«сменить образ»"
        "set_size_to" -> "«установить размер»"
        "change_size_by" -> "«изменить размер»"
        "play_sound" -> "«играть звук»"
        "set_volume_to" -> "«установить громкость»"
        "wait" -> "«ждать»"
        "broadcast" -> "«отправить сообщение»"
        "broadcast_wait" -> "«отправить сообщение и ждать»"
        "forever" -> "«повторять всегда»"
        else -> brickType
    }

    // ------------------------------------------------------------------
    // Вспомогательные методы
    // ------------------------------------------------------------------

    private fun addColoredLook(scene: Scene, sprite: Sprite, lookName: String, colorHex: String): JsonObject {
        val color = runCatching { Color.parseColor("#" + colorHex.removePrefix("#")) }.getOrNull()
            ?: return errorResult("Неверный цвет «$colorHex», используйте формат #RRGGBB")
        val imagesDir = File(scene.directory, Constants.IMAGE_DIRECTORY_NAME)
        if (!imagesDir.exists() && !imagesDir.mkdirs()) {
            return errorResult("Не удалось создать каталог образов")
        }
        val file = File(imagesDir, sanitizeFileName(lookName) + Constants.DEFAULT_IMAGE_EXTENSION)
        val bitmap = Bitmap.createBitmap(LOOK_SIZE, LOOK_SIZE, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).drawColor(color)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            return errorResult("Не удалось создать файл образа: ${e.message}")
        } finally {
            bitmap.recycle()
        }
        val lookData = LookData(lookName, file)
        sprite.lookList.add(lookData)
        lookData.collisionInformation.calculate()
        sprite.look.setLookData(lookData)
        return okResult("Образ «$lookName» добавлен спрайту «${sprite.name}»")
    }

    private fun findSprite(scene: Scene, name: String): Sprite? = scene.getSprite(name)

    private fun okResult(summary: String, extra: JsonObject.() -> Unit = {}): JsonObject =
        JsonObject().apply {
            addProperty("ok", true)
            addProperty("summary", summary)
            extra()
        }

    private fun errorResult(message: String): JsonObject = JsonObject().apply {
        addProperty("ok", false)
        addProperty("error", message)
    }

    private fun str(args: JsonObject, key: String, default: String = ""): String =
        args.get(key)?.takeIf { !it.isJsonNull }?.asString ?: default

    private fun dbl(args: JsonObject, key: String, default: Double): Double =
        runCatching { args.get(key)?.asDouble }.getOrNull() ?: default

    private fun int(args: JsonObject, key: String, default: Int): Int =
        runCatching { args.get(key)?.asInt }.getOrNull() ?: default

    private fun sanitizeFileName(name: String): String {
        val builder = StringBuilder()
        name.lowercase().forEach { c ->
            builder.append(if (c.isLetterOrDigit()) c else '_')
        }
        return builder.toString().ifEmpty { "look" }
    }

    // ------------------------------------------------------------------
    // JSON-схема инструментов (function calling)
    // ------------------------------------------------------------------

    private fun buildTools(): JsonArray {
        val tools = JsonArray()

        tools.add(tool(
            "add_sprite",
            "Создаёт нового актёра (спрайт) в текущей сцене проекта. Может сразу создать цветной образ.",
            "name" to param("string", "Имя нового спрайта, например «Кот»", true),
            "color" to param("string", "Цвет образа в формате #RRGGBB, например #FF5722 (необязательно)", false)
        ))
        tools.add(tool(
            "rename_sprite",
            "Переименовывает спрайт.",
            "name" to param("string", "Текущее имя спрайта", true),
            "new_name" to param("string", "Новое имя спрайта", true)
        ))
        tools.add(tool(
            "delete_sprite",
            "Удаляет спрайт из сцены (фон удалить нельзя).",
            "name" to param("string", "Имя спрайта", true)
        ))
        tools.add(tool(
            "add_look",
            "Добавляет спрайту новый цветной образ (внешний вид).",
            "sprite_name" to param("string", "Имя спрайта", true),
            "look_name" to param("string", "Имя образа, например «Кот 2»", true),
            "color" to param("string", "Цвет образа #RRGGBB, например #2196F3", true)
        ))
        tools.add(tool(
            "rename_look",
            "Переименовывает образ спрайта.",
            "sprite_name" to param("string", "Имя спрайта", true),
            "look_name" to param("string", "Текущее имя образа", true),
            "new_name" to param("string", "Новое имя образа", true)
        ))
        tools.add(tool(
            "delete_look",
            "Удаляет образ спрайта.",
            "sprite_name" to param("string", "Имя спрайта", true),
            "look_name" to param("string", "Имя образа", true)
        ))
        tools.add(tool(
            "set_sprite_look",
            "Делает указанный образ спрайта активным (видимым).",
            "sprite_name" to param("string", "Имя спрайта", true),
            "look_name" to param("string", "Имя образа", true)
        ))
        tools.add(tool(
            "change_background",
            "Изменяет фон сцены: добавляет фону новый цветной образ и делает его активным.",
            "color" to param("string", "Цвет фона #RRGGBB, например #4CAF50", true),
            "look_name" to param("string", "Имя образа фона (необязательно, по умолчанию «Фон»)", false)
        ))
        tools.add(tool(
            "add_sound_to_sprite",
            "Копирует существующий звук одного спрайта другому спрайту (создать звуковой файл с нуля невозможно).",
            "sprite_name" to param("string", "Спрайт, которому добавить звук", true),
            "source_sprite_name" to param("string", "Спрайт, у которого взять звук", true),
            "source_sound_name" to param("string", "Имя звука-источника", true),
            "new_sound_name" to param("string", "Имя нового звука", true)
        ))
        tools.add(tool(
            "rename_sound",
            "Переименовывает звук спрайта.",
            "sprite_name" to param("string", "Имя спрайта", true),
            "sound_name" to param("string", "Текущее имя звука", true),
            "new_name" to param("string", "Новое имя звука", true)
        ))
        tools.add(tool(
            "delete_sound",
            "Удаляет звук спрайта.",
            "sprite_name" to param("string", "Имя спрайта", true),
            "sound_name" to param("string", "Имя звука", true)
        ))
        tools.add(tool(
            "add_script",
            "Добавляет спрайту новый скрипт. script_type: start — при запуске, tapped — когда спрайт нажат, receive — когда получено сообщение (нужен message), background_changes — когда фон меняется. Возвращает script_index для добавления блоков.",
            "sprite_name" to param("string", "Имя спрайта", true),
            "script_type" to param("string", "start | tapped | receive | background_changes", true),
            "message" to param("string", "Сообщение для script_type=receive", false)
        ))
        tools.add(tool(
            "delete_script",
            "Удаляет скрипт спрайта по индексу.",
            "sprite_name" to param("string", "Имя спрайта", true),
            "script_index" to param("integer", "Индекс скрипта (0 — первый)", true)
        ))
        tools.add(tool(
            "clear_script",
            "Удаляет все блоки из скрипта (сам скрипт остаётся).",
            "sprite_name" to param("string", "Имя спрайта", true),
            "script_index" to param("integer", "Индекс скрипта", true)
        ))
        tools.add(tool(
            "add_brick",
            "Добавляет блок в скрипт спрайта. brick_type — тип блока, остальные параметры зависят от типа: move_n_steps{steps}, turn_left/turn_right{degrees}, go_to_xy{x,y}, go_to_sprite{target_sprite}, point_in_direction{degrees}, glide_to{x,y,seconds}, say{text}, say_for{text,seconds}, think{text}, show, hide, set_look{look_name}, set_size_to{percent}, change_size_by{percent}, play_sound{sound_name}, set_volume_to{volume}, wait{seconds}, broadcast{message}, broadcast_wait{message}, forever (цикл; возвращает loop_brick_index для вложенных блоков). position — необязательный индекс вставки.",
            "sprite_name" to param("string", "Имя спрайта", true),
            "script_index" to param("integer", "Индекс скрипта", true),
            "brick_type" to param("string", "Тип блока (список в описании)", true),
            "steps" to param("number", "Шаги для move_n_steps", false),
            "degrees" to param("number", "Градусы для turn_left/turn_right/point_in_direction", false),
            "x" to param("integer", "Координата X", false),
            "y" to param("integer", "Координата Y", false),
            "target_sprite" to param("string", "Целевой спрайт для go_to_sprite", false),
            "seconds" to param("number", "Секунды для glide_to/say_for/wait", false),
            "text" to param("string", "Текст для say/say_for/think", false),
            "look_name" to param("string", "Имя образа для set_look", false),
            "percent" to param("number", "Проценты для set_size_to/change_size_by", false),
            "sound_name" to param("string", "Имя звука для play_sound", false),
            "volume" to param("number", "Громкость 0-100 для set_volume_to", false),
            "message" to param("string", "Сообщение для broadcast/broadcast_wait", false),
            "position" to param("integer", "Индекс вставки (0 — в начало)", false)
        ))
        tools.add(tool(
            "add_brick_to_loop",
            "Добавляет блок внутрь цикла forever (loop_brick_index из add_brick с brick_type=forever). Параметры блока — как в add_brick.",
            "sprite_name" to param("string", "Имя спрайта", true),
            "script_index" to param("integer", "Индекс скрипта", true),
            "loop_brick_index" to param("integer", "Индекс блока forever в скрипте", true),
            "brick_type" to param("string", "Тип блока (список в add_brick)", true),
            "steps" to param("number", "Шаги", false),
            "degrees" to param("number", "Градусы", false),
            "x" to param("integer", "Координата X", false),
            "y" to param("integer", "Координата Y", false),
            "target_sprite" to param("string", "Целевой спрайт", false),
            "seconds" to param("number", "Секунды", false),
            "text" to param("string", "Текст", false),
            "look_name" to param("string", "Имя образа", false),
            "percent" to param("number", "Проценты", false),
            "sound_name" to param("string", "Имя звука", false),
            "volume" to param("number", "Громкость", false),
            "message" to param("string", "Сообщение", false)
        ))
        tools.add(tool(
            "delete_brick",
            "Удаляет блок из скрипта по индексу.",
            "sprite_name" to param("string", "Имя спрайта", true),
            "script_index" to param("integer", "Индекс скрипта", true),
            "brick_index" to param("integer", "Индекс блока", true)
        ))
        tools.add(tool(
            "add_any_brick",
            "УНИВЕРСАЛЬНЫЙ инструмент: ставит ЛЮБОЙ блок NewCode по имени класса, даже редкий. brick_class — точное имя класса блока, например SetVariableBrick, ChangeVariableBrick, ShowTextBrick, SetXBrick, WhenStartedBrick, RepeatBrick, IfThenLogicBeginBrick, NextLookBrick, StopAllSoundsBrick, SetPenSizeBrick, GoNStepsBackBrick, SpeakBrick, VibrationBrick, SetGravityBrick, CloneBrick и т.д. (полный список — через search_bricks). 3D-блоки: CreateCubeBrick, CreateSphereBrick, CreatePyramidBrick, CreateCylinderBrick (параметры size — размер, color — цвет #RRGGBB). Параметры передаются в params: список из {name, value} — они заполняют конструктор или сеттеры блока; value может быть числом, строкой, true/false или именем объекта (спрайт, образ, звук, переменная, список). Для блоков с формулой (SetVariableBrick и др.) передайте param name=value со строковым выражением, например «1» или «10». position — индекс вставки.",
            "sprite_name" to param("string", "Имя спрайта", true),
            "script_index" to param("integer", "Индекс скрипта", true),
            "brick_class" to param("string", "Точное имя класса блока, например SetVariableBrick", true),
            "params" to param("array", "Список параметров [{\"name\": \"...\", \"value\": ...}]", true),
            "position" to param("integer", "Индекс вставки (0 — в начало)", false)
        ))
        tools.add(tool(
            "create_3d_object",
            "Создаёт 3D-фигуру: генерирует объёмный образ (куб, сфера, пирамида или цилиндр) и сразу делает его активным у указанного спрайта. shape: cube | sphere | pyramid | cylinder. size — размер в пикселях (по умолчанию 100). color — цвет #RRGGBB (по умолчанию зелёный #4CAF50). 3D-фигура — это готовый образ спрайта: его можно двигать, вращать и менять размер обычными блоками.",
            "sprite_name" to param("string", "Имя спрайта, которому назначить фигуру", true),
            "shape" to param("string", "cube | sphere | pyramid | cylinder", true),
            "size" to param("number", "Размер в пикселях (по умолчанию 100)", false),
            "color" to param("string", "Цвет #RRGGBB, например #FF5722 (по умолчанию #4CAF50)", false)
        ))
        tools.add(tool(
            "add_variable",
            "Создаёт переменную в проекте или у спрайта.",
            "name" to param("string", "Имя переменной, например «очки»", true),
            "value" to param("string", "Начальное значение (необязательно)", false),
            "scope" to param("string", "project или sprite", false),
            "sprite_name" to param("string", "Имя спрайта (если scope=sprite)", false)
        ))
        tools.add(tool(
            "set_variable_value",
            "Устанавливает значение существующей переменной.",
            "name" to param("string", "Имя переменной", true),
            "value" to param("string", "Новое значение", true)
        ))
        tools.add(tool(
            "add_list",
            "Создаёт список (массив) в проекте или у спрайта.",
            "name" to param("string", "Имя списка", true),
            "value" to param("array", "Начальные значения списка (необязательно)", false),
            "scope" to param("string", "project или sprite", false),
            "sprite_name" to param("string", "Имя спрайта (если scope=sprite)", false)
        ))
        tools.add(tool(
            "search_bricks",
            "Ищет блоки по имени или параметру. Возвращает точные имена классов и их конструкторы/сеттеры. Используйте перед add_any_brick, если не уверены в имени класса.",
            "query" to param("string", "Поисковый запрос, например variable, list, text, physics, clone", true)
        ))
        tools.add(tool(
            "get_project_state",
            "Возвращает актуальное состояние проекта (сцены, спрайты, образы, звуки, скрипты и блоки). Вызывайте, когда нужно проверить результат своих действий или освежить память о проекте в середине разговора.",
            "query" to param("string", "Необязательный комментарий, зачем нужно состояние (игнорируется)", false)
        ))
        tools.add(tool(
            "set_project_name",
            "Переименовывает проект.",
            "name" to param("string", "Новое имя проекта", true)
        ))

        return tools
    }

    private fun param(type: String, description: String, required: Boolean): Triple<String, String, Boolean> =
        Triple(type, description, required)

    private fun tool(name: String, description: String, vararg params: Pair<String, Triple<String, String, Boolean>>): JsonObject {
        val properties = JsonObject()
        val required = JsonArray()
        params.forEach { (paramName, info) ->
            val p = JsonObject()
            p.addProperty("type", info.first)
            p.addProperty("description", info.second)
            properties.add(paramName, p)
            if (info.third) {
                required.add(paramName)
            }
        }
        val parameters = JsonObject()
        parameters.addProperty("type", "object")
        parameters.add("properties", properties)
        parameters.add("required", required)
        val function = JsonObject()
        function.addProperty("name", name)
        function.addProperty("description", description)
        function.add("parameters", parameters)
        return JsonObject().apply {
            addProperty("type", "function")
            add("function", function)
        }
    }
}
