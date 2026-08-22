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
package org.catrobat.catroid.content.bricks

import android.content.Context
import android.view.View
import android.widget.TextView
import org.catrobat.catroid.R
import org.catrobat.catroid.content.Sprite
import org.catrobat.catroid.content.actions.Change3DObjectColorAction
import org.catrobat.catroid.content.actions.ScriptSequenceAction

/**
 * Блок «Изменить цвет 3D-объекта»: перерисовывает 3D-фигуру (образ)
 * с заданным именем в новый цвет.
 */
class Change3DObjectColorBrick : BrickBaseType {

    var name: String = ""
    var color: String = "#4CAF50"

    constructor() : super()

    constructor(name: String, color: String) : super() {
        this.name = name
        this.color = color
    }

    override fun getViewResource(): Int = R.layout.brick_change_3d_color

    override fun getView(context: Context): View {
        super.getView(context)
        view.findViewById<TextView>(R.id.brick_3d_text_view).text =
            context.getString(R.string.brick_change_3d_color) +
                " (${name.trim().ifEmpty { "-" }} → $color)"
        return view
    }

    override fun addActionToSequence(sprite: Sprite, sequence: ScriptSequenceAction) {
        val action = Change3DObjectColorAction()
        action.setSprite(sprite)
        action.setObjectName(name)
        action.setColorHex(color)
        sequence.addAction(action)
    }
}
