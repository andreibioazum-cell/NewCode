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
package org.catrobat.catroid.common

import android.graphics.Bitmap
import android.graphics.Color
import org.catrobat.catroid.content.Scene
import org.catrobat.catroid.content.Sprite
import org.catrobat.catroid.io.StorageOperations
import org.catrobat.catroid.ui.recyclerview.util.UniqueNameProvider
import java.io.File
import java.io.IOException

/**
 * Создаёт образ (LookData) спрайта из 3D-фигуры: генерирует PNG-файл
 * в каталоге сцены и регистрирует его у спрайта.
 *
 * Имя файла образа хранит метаданные фигуры: {shape}_{size}_{name}.png,
 * например cube_100_cube1.png — по ним блок «Изменить цвет 3D-объекта»
 * может перерисовать фигуру в новый цвет.
 */
object ThreeDLookGenerator {

    private const val DEFAULT_COLOR = "#4CAF50"

    /**
     * Генерирует 3D-образ и делает его активным у спрайта.
     *
     * @param name имя фигуры (имя образа), например «cube1»; если пусто —
     * используется имя фигуры по умолчанию («Cube», «Sphere», ...)
     * @return имя созданного образа или null при ошибке
     */
    fun createLook(
        scene: Scene,
        sprite: Sprite,
        shape: ThreeDShape,
        name: String,
        sizePx: Int,
        colorHex: String
    ): String? {
        return try {
            val colorInt = parseColor(colorHex)
            val bitmap: Bitmap = ThreeDShapeGenerator.generate(shape, sizePx, colorInt)

            val imagesDir = File(scene.directory, Constants.IMAGE_DIRECTORY_NAME)
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                return null
            }
            val shapePrefix = when (shape) {
                ThreeDShape.CUBE -> "cube"
                ThreeDShape.SPHERE -> "sphere"
                ThreeDShape.PYRAMID -> "pyramid"
                ThreeDShape.CYLINDER -> "cylinder"
            }
            val requestedName = name.trim().ifEmpty { defaultName(shape) }
            val uniqueName = UniqueNameProvider().getUniqueNameInNameables(requestedName, sprite.lookList)
            val file = File(
                imagesDir,
                "${shapePrefix}_${sizePx}_" + sanitizeFileName(uniqueName) + Constants.DEFAULT_IMAGE_EXTENSION
            )
            StorageOperations.compressBitmapToPng(bitmap, file)

            val lookData = LookData(uniqueName, file)
            sprite.lookList.add(lookData)
            lookData.collisionInformation.calculate()
            sprite.look.setLookData(lookData)
            uniqueName
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Перерисовывает существующий 3D-образ (по имени) в новый цвет.
     * Форма и размер берутся из имени файла образа.
     *
     * @return true, если образ найден и перерисован
     */
    fun recolorLook(sprite: Sprite, lookName: String, colorHex: String): Boolean {
        val lookData = sprite.lookList.firstOrNull { it.name == lookName } ?: return false
        val meta = parseFileName(lookData.file?.name ?: return false) ?: return false
        val colorInt = parseColor(colorHex)
        val bitmap = ThreeDShapeGenerator.generate(meta.shape, meta.sizePx, colorInt)
        return try {
            StorageOperations.compressBitmapToPng(bitmap, lookData.file)
            lookData.collisionInformation.calculate()
            if (sprite.look.lookData === lookData) {
                sprite.look.setLookData(lookData)
            }
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun parseColor(colorHex: String): Int = runCatching {
        Color.parseColor("#" + colorHex.removePrefix("#"))
    }.getOrDefault(Color.parseColor(DEFAULT_COLOR))

    private fun defaultName(shape: ThreeDShape): String = when (shape) {
        ThreeDShape.CUBE -> "Cube"
        ThreeDShape.SPHERE -> "Sphere"
        ThreeDShape.PYRAMID -> "Pyramid"
        ThreeDShape.CYLINDER -> "Cylinder"
    }

    private data class ShapeMeta(val shape: ThreeDShape, val sizePx: Int)

    private fun parseFileName(fileName: String): ShapeMeta? {
        // Формат: {shape}_{size}_{name}.png
        val base = fileName.substringBeforeLast('.')
        val parts = base.split('_')
        if (parts.size < 2) {
            return null
        }
        val shape = when (parts[0]) {
            "cube" -> ThreeDShape.CUBE
            "sphere" -> ThreeDShape.SPHERE
            "pyramid" -> ThreeDShape.PYRAMID
            "cylinder" -> ThreeDShape.CYLINDER
            else -> return null
        }
        val size = parts[1].toIntOrNull() ?: return null
        return ShapeMeta(shape, size)
    }

    private fun sanitizeFileName(name: String): String {
        val builder = StringBuilder()
        name.lowercase().forEach { c ->
            builder.append(if (c.isLetterOrDigit()) c else '_')
        }
        return builder.toString().ifEmpty { "shape" }
    }
}
