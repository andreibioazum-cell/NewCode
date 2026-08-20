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

import org.catrobat.catroid.content.Script

/**
 * Carries break/continue signals from the C bricks (BreakBrick/ContinueBrick) to
 * the innermost enclosing loop of the same script. The stage action tree is
 * walked synchronously on a single thread, and loop actions consult this
 * controller right after their body acted, so the innermost loop always
 * consumes the signal first — including bodies nested inside if-branches.
 */
object LoopController {

    private const val SIGNAL_BREAK = 1
    private const val SIGNAL_CONTINUE = 2

    private val signals = HashMap<Script, Int>()

    @JvmStatic
    @Synchronized
    fun signalBreak(script: Script?) {
        if (script != null) {
            signals[script] = SIGNAL_BREAK
        }
    }

    @JvmStatic
    @Synchronized
    fun signalContinue(script: Script?) {
        if (script != null) {
            signals[script] = SIGNAL_CONTINUE
        }
    }

    @JvmStatic
    @Synchronized
    fun consumeBreak(script: Script?): Boolean = signals[script] == SIGNAL_BREAK && signals.remove(script) != null

    @JvmStatic
    @Synchronized
    fun consumeContinue(script: Script?): Boolean =
        signals[script] == SIGNAL_CONTINUE && signals.remove(script) != null

    @JvmStatic
    @Synchronized
    fun clearAll() {
        signals.clear()
    }
}
