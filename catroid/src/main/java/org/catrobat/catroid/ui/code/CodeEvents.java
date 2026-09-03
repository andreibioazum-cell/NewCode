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
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.catrobat.catroid.ui.code;

import org.catrobat.catroid.R;
import org.catrobat.catroid.content.BroadcastScript;
import org.catrobat.catroid.content.EmptyScript;
import org.catrobat.catroid.content.Script;
import org.catrobat.catroid.content.StartScript;
import org.catrobat.catroid.content.WhenBackgroundChangesScript;
import org.catrobat.catroid.content.WhenBounceOffScript;
import org.catrobat.catroid.content.WhenClonedScript;
import org.catrobat.catroid.content.WhenConditionScript;
import org.catrobat.catroid.content.WhenScript;
import org.catrobat.catroid.content.WhenTouchDownScript;
import org.catrobat.catroid.content.bricks.UserDefinedReceiverBrick;

import android.content.Context;

/**
 * Human readable event names for scripts and script creation helpers.
 */
public final class CodeEvents {

	public static final String TYPE_STARTED = "started";
	public static final String TYPE_TOUCHED = "touched";
	public static final String TYPE_BROADCAST = "broadcast";
	public static final String TYPE_CLONED = "cloned";

	private CodeEvents() {
	}

	/**
	 * Returns a short, language independent event title for the script.
	 */
	public static String eventTitle(Script script, Context context) {
		if (script instanceof StartScript) {
			return context.getString(R.string.code_event_started);
		}
		if (script instanceof WhenScript) {
			return context.getString(R.string.code_event_touched);
		}
		if (script instanceof WhenClonedScript) {
			return context.getString(R.string.code_event_cloned);
		}
		if (script instanceof WhenTouchDownScript) {
			return context.getString(R.string.code_event_background_touched);
		}
		if (script instanceof WhenBackgroundChangesScript) {
			return context.getString(R.string.code_event_background_changed);
		}
		if (script instanceof WhenBounceOffScript) {
			return context.getString(R.string.code_event_bounce_off);
		}
		if (script instanceof WhenConditionScript) {
			return context.getString(R.string.code_event_condition);
		}
		if (script instanceof BroadcastScript) {
			String message = ((BroadcastScript) script).getBroadcastMessage();
			return context.getString(R.string.code_event_broadcast, message == null ? "?" : message);
		}
		if (script instanceof EmptyScript) {
			return context.getString(R.string.code_event_empty);
		}
		if (script.getScriptBrick() instanceof UserDefinedReceiverBrick) {
			return context.getString(R.string.code_event_user_script, "user");
		}
		return script.getClass().getSimpleName();
	}

	/**
	 * Creates a new empty script of the given type.
	 *
	 * @param type          one of {@link #TYPE_STARTED}, {@link #TYPE_TOUCHED}, {@link #TYPE_BROADCAST},
	 *                      {@link #TYPE_CLONED}
	 * @param broadcastName the message name if the type is {@link #TYPE_BROADCAST}, else ignored
	 */
	public static Script createNewScript(String type, String broadcastName) {
		if (TYPE_TOUCHED.equals(type)) {
			return new WhenScript();
		}
		if (TYPE_BROADCAST.equals(type)) {
			return new BroadcastScript(broadcastName == null ? "" : broadcastName);
		}
		if (TYPE_CLONED.equals(type)) {
			return new WhenClonedScript();
		}
		return new StartScript();
	}
}
