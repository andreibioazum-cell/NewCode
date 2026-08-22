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
import org.catrobat.catroid.content.actions.Delete3DObjectAction
import org.catrobat.catroid.content.actions.ScriptSequenceAction

/**
 * Блок «Удалить 3D-объект»: удаляет 3D-фигуру (образ) с заданным именем.
 */
class Delete3DObjectBrick : BrickBaseType {

    var name: String = ""

    constructor() : super()

    constructor(name: String) : super() {
        this.name = name
    }

    override fun getViewResource(): Int = R.layout.brick_delete_3d_object

    override fun getView(context: Context): View {
        super.getView(context)
        view.findViewById<TextView>(R.id.brick_3d_text_view).text =
            context.getString(R.string.brick_delete_3d_object) +
                " (" + name.trim().ifEmpty { "-" } + ")"
        return view
    }

    override fun addActionToSequence(sprite: Sprite, sequence: ScriptSequenceAction) {
        val action = Delete3DObjectAction()
        action.setSprite(sprite)
        action.setObjectName(name)
        sequence.addAction(action)
    }
}
