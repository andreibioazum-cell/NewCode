/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2025 The Catrobat Team
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
package org.catrobat.catroid.ui.adapter

import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import org.catrobat.catroid.content.bricks.Brick
import org.catrobat.catroid.content.bricks.BrickBaseType

class PrototypeBrickAdapter(private var brickList: List<Brick>) : BaseAdapter() {
    private val layoutViewTypes = HashMap<Int, Int>()
    override fun getCount(): Int = brickList.size

    override fun getItem(position: Int): Brick = brickList[position]

    override fun getItemId(position: Int): Long {
        val id = brickList[position].brickID ?: return System.identityHashCode(brickList[position]).toLong()
        return id.mostSignificantBits xor id.leastSignificantBits
    }

    override fun hasStableIds(): Boolean = true

    override fun getViewTypeCount(): Int = MAX_BRICK_VIEW_TYPES

    override fun getItemViewType(position: Int): Int {
        val layout = (brickList[position] as? BrickBaseType)?.viewResource ?: return 0
        return layoutViewTypes.getOrPut(layout) {
            if (layoutViewTypes.size < MAX_BRICK_VIEW_TYPES) layoutViewTypes.size
            else (layout and Int.MAX_VALUE) % MAX_BRICK_VIEW_TYPES
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View? {
        val brick = brickList[position]
        (brick as? BrickBaseType)?.prepareForReuse(convertView)
        return parent?.context?.let { brick.getPrototypeView(it) }
    }

    fun replaceList(list: List<Brick>) {
        brickList = list
        notifyDataSetChanged()
    }

    private companion object {
        const val MAX_BRICK_VIEW_TYPES = 256
    }
}
