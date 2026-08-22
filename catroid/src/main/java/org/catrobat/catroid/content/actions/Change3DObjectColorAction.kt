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
package org.catrobat.catroid.content.actions

import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import org.catrobat.catroid.common.ThreeDLookGenerator
import org.catrobat.catroid.content.Sprite

/**
 * Перерисовывает 3D-образ с заданным именем в новый цвет.
 */
class Change3DObjectColorAction : TemporalAction() {

    private var sprite: Sprite? = null
    private var objectName: String = ""
    private var colorHex: String = "#4CAF50"
    private var executed = false

    fun setSprite(sprite: Sprite?) {
        this.sprite = sprite
    }

    fun setObjectName(objectName: String) {
        this.objectName = objectName
    }

    fun setColorHex(colorHex: String) {
        this.colorHex = colorHex
    }

    override fun update(percent: Float) {
        if (executed) {
            return
        }
        executed = true
        val sp = sprite ?: return
        runCatching {
            ThreeDLookGenerator.recolorLook(sp, objectName, colorHex)
        }
    }
}
