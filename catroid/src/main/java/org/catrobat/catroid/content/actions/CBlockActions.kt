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

import android.util.Log
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import org.catrobat.catroid.cmemory.CMemory
import org.catrobat.catroid.content.Scope
import org.catrobat.catroid.formulaeditor.Formula
import org.catrobat.catroid.formulaeditor.InterpretationException
import org.catrobat.catroid.formulaeditor.UserVariable

private const val TAG = "CBlockActions"

private fun Formula?.interpretSafely(scope: Scope?): Any? = try {
    this?.interpretObject(scope) ?: 0.0
} catch (interpretationException: InterpretationException) {
    Log.d(TAG, "Formula interpretation for this specific Brick failed.", interpretationException)
    null
}

private fun Formula?.interpretLongSafely(scope: Scope?): Long? = try {
    this?.interpretDouble(scope)?.toLong() ?: 0L
} catch (interpretationException: InterpretationException) {
    Log.d(TAG, "Formula interpretation for this specific Brick failed.", interpretationException)
    null
}

/** ptr = malloc(size) — stores the address (0 = NULL) in a user variable. */
class MallocAction : TemporalAction() {
    var scope: Scope? = null
    var cMemory: CMemory? = null
    var size: Formula? = null
    var userVariable: UserVariable? = null

    override fun update(percent: Float) {
        val memory = cMemory ?: return
        val sizeValue = size.interpretLongSafely(scope) ?: return
        userVariable?.setValue(memory.malloc(sizeValue.toInt()).toDouble())
    }
}

/** ptr = calloc(count, size) — zero-initialized allocation. */
class CallocAction : TemporalAction() {
    var scope: Scope? = null
    var cMemory: CMemory? = null
    var count: Formula? = null
    var size: Formula? = null
    var userVariable: UserVariable? = null

    override fun update(percent: Float) {
        val memory = cMemory ?: return
        val countValue = count.interpretLongSafely(scope) ?: return
        val sizeValue = size.interpretLongSafely(scope) ?: return
        userVariable?.setValue(memory.calloc(countValue.toInt(), sizeValue.toInt()).toDouble())
    }
}

/** ptr = realloc(oldPointer, size) — contents are preserved. */
class ReallocAction : TemporalAction() {
    var scope: Scope? = null
    var cMemory: CMemory? = null
    var pointer: Formula? = null
    var size: Formula? = null
    var userVariable: UserVariable? = null

    override fun update(percent: Float) {
        val memory = cMemory ?: return
        val pointerValue = pointer.interpretLongSafely(scope) ?: return
        val sizeValue = size.interpretLongSafely(scope) ?: return
        userVariable?.setValue(memory.realloc(pointerValue, sizeValue.toInt()).toDouble())
    }
}

/** free(pointer) */
class FreeMemoryAction : TemporalAction() {
    var scope: Scope? = null
    var cMemory: CMemory? = null
    var pointer: Formula? = null

    override fun update(percent: Float) {
        val memory = cMemory ?: return
        val pointerValue = pointer.interpretLongSafely(scope) ?: return
        memory.free(pointerValue)
    }
}

/** memcpy(destination, source, size) */
class MemcpyAction : TemporalAction() {
    var scope: Scope? = null
    var cMemory: CMemory? = null
    var destination: Formula? = null
    var source: Formula? = null
    var size: Formula? = null

    override fun update(percent: Float) {
        val memory = cMemory ?: return
        val destinationValue = destination.interpretLongSafely(scope) ?: return
        val sourceValue = source.interpretLongSafely(scope) ?: return
        val sizeValue = size.interpretLongSafely(scope) ?: return
        memory.memcpy(destinationValue, sourceValue, sizeValue.toInt())
    }
}

/** memset(pointer, value, size) */
class MemsetAction : TemporalAction() {
    var scope: Scope? = null
    var cMemory: CMemory? = null
    var pointer: Formula? = null
    var value: Formula? = null
    var size: Formula? = null

    override fun update(percent: Float) {
        val memory = cMemory ?: return
        val pointerValue = pointer.interpretLongSafely(scope) ?: return
        val valueByte = value.interpretLongSafely(scope) ?: return
        val sizeValue = size.interpretLongSafely(scope) ?: return
        memory.memset(pointerValue, valueByte.toInt(), sizeValue.toInt())
    }
}

/** typedef name = baseType */
class TypedefAction : TemporalAction() {
    var scope: Scope? = null
    var cMemory: CMemory? = null
    var name: Formula? = null
    var baseType: Formula? = null

    override fun update(percent: Float) {
        val memory = cMemory ?: return
        val nameValue = name.interpretSafely(scope)?.toString() ?: return
        val baseTypeValue = baseType.interpretSafely(scope)?.toString() ?: return
        memory.defineTypedef(nameValue, baseTypeValue)
    }
}

/** variable = (type)value */
class CastAction : TemporalAction() {
    var scope: Scope? = null
    var cMemory: CMemory? = null
    var value: Formula? = null
    var type: Formula? = null
    var userVariable: UserVariable? = null

    override fun update(percent: Float) {
        val memory = cMemory ?: return
        val valueObject = value.interpretSafely(scope) ?: return
        val typeName = type.interpretSafely(scope)?.toString()
        userVariable?.setValue(memory.cast(valueObject, typeName))
    }
}

/** *(type*)(pointer + offset) = value */
class PointerSetAction : TemporalAction() {
    var scope: Scope? = null
    var cMemory: CMemory? = null
    var pointer: Formula? = null
    var offset: Formula? = null
    var value: Formula? = null
    var type: Formula? = null

    override fun update(percent: Float) {
        val memory = cMemory ?: return
        val pointerValue = pointer.interpretLongSafely(scope) ?: return
        val offsetValue = offset.interpretLongSafely(scope) ?: return
        val valueObject = value.interpretSafely(scope) ?: return
        val typeName = type.interpretSafely(scope)?.toString()
        memory.pointerSet(pointerValue, offsetValue.toInt(), valueObject, typeName)
    }
}

/** variable = *(type*)(pointer + offset) */
class PointerGetAction : TemporalAction() {
    var scope: Scope? = null
    var cMemory: CMemory? = null
    var pointer: Formula? = null
    var offset: Formula? = null
    var type: Formula? = null
    var userVariable: UserVariable? = null

    override fun update(percent: Float) {
        val memory = cMemory ?: return
        val pointerValue = pointer.interpretLongSafely(scope) ?: return
        val offsetValue = offset.interpretLongSafely(scope) ?: return
        val typeName = type.interpretSafely(scope)?.toString()
        userVariable?.setValue(memory.pointerGet(pointerValue, offsetValue.toInt(), typeName))
    }
}

/** break — leaves the innermost enclosing loop of this script. */
class BreakLoopAction : TemporalAction() {
    var script: org.catrobat.catroid.content.Script? = null

    override fun update(percent: Float) {
        LoopController.signalBreak(script)
    }
}

/** continue — skips to the next iteration of the innermost loop of this script. */
class ContinueLoopAction : TemporalAction() {
    var script: org.catrobat.catroid.content.Script? = null

    override fun update(percent: Float) {
        LoopController.signalContinue(script)
    }
}
