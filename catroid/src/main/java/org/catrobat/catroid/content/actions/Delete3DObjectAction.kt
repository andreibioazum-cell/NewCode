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
import org.catrobat.catroid.content.Sprite

/**
 * Удаляет 3D-образ (фигуру) с заданным именем у спрайта.
 */
class Delete3DObjectAction : TemporalAction() {

    private var sprite: Sprite? = null
    private var objectName: String = ""
    private var executed = false

    fun setSprite(sprite: Sprite?) {
        this.sprite = sprite
    }

    fun setObjectName(objectName: String) {
        this.objectName = objectName
    }

    override fun update(percent: Float) {
        if (executed) {
            return
        }
        executed = true
        val sp = sprite ?: return
        val lookData = sp.lookList.firstOrNull { it.name == objectName } ?: return
        val index = sp.lookList.indexOf(lookData)
        if (sp.look.lookData === lookData) {
            val nextIndex = if (sp.lookList.size > 1) {
                (index + 1).coerceAtMost(sp.lookList.size - 1)
            } else {
                -1
            }
            if (nextIndex in sp.lookList.indices && sp.lookList[nextIndex] !== lookData) {
                sp.look.setLookData(sp.lookList[nextIndex])
            }
        }
        sp.lookList.removeAt(index)
        runCatching { lookData.invalidate() }
    }
}
