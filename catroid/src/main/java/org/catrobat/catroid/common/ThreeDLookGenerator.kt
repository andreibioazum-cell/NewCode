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
 */
object ThreeDLookGenerator {

    private const val DEFAULT_COLOR = "#4CAF50"

    /**
     * Генерирует 3D-образ и делает его активным у спрайта.
     *
     * @return имя созданного образа или null при ошибке
     */
    fun createLook(
        scene: Scene,
        sprite: Sprite,
        shape: ThreeDShape,
        sizePx: Int,
        colorHex: String
    ): String? {
        return try {
            val colorInt = runCatching { Color.parseColor("#" + colorHex.removePrefix("#")) }
                .getOrDefault(Color.parseColor(DEFAULT_COLOR))
            val bitmap: Bitmap = ThreeDShapeGenerator.generate(shape, sizePx, colorInt)

            val imagesDir = File(scene.directory, Constants.IMAGE_DIRECTORY_NAME)
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                return null
            }
            val baseName = when (shape) {
                ThreeDShape.CUBE -> "Cube"
                ThreeDShape.SPHERE -> "Sphere"
                ThreeDShape.PYRAMID -> "Pyramid"
                ThreeDShape.CYLINDER -> "Cylinder"
            }
            val uniqueName = UniqueNameProvider().getUniqueNameInNameables(baseName, sprite.lookList)
            val file = File(imagesDir, sanitizeFileName(uniqueName) + Constants.DEFAULT_IMAGE_EXTENSION)
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

    private fun sanitizeFileName(name: String): String {
        val builder = StringBuilder()
        name.lowercase().forEach { c ->
            builder.append(if (c.isLetterOrDigit()) c else '_')
        }
        return builder.toString().ifEmpty { "shape" }
    }
}
