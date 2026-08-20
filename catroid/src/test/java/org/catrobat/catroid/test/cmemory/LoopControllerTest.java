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
package org.catrobat.catroid.test.cmemory;

import org.catrobat.catroid.content.Script;
import org.catrobat.catroid.content.StartScript;
import org.catrobat.catroid.content.actions.LoopController;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@RunWith(JUnit4.class)
public class LoopControllerTest {

	private Script firstScript;
	private Script secondScript;

	@Before
	public void setUp() {
		LoopController.clearAll();
		firstScript = new StartScript();
		secondScript = new StartScript();
	}

	@Test
	public void testBreakSignalIsConsumedOnceByOwningScript() {
		LoopController.signalBreak(firstScript);
		assertTrue(LoopController.consumeBreak(firstScript));
		assertFalse(LoopController.consumeBreak(firstScript));
	}

	@Test
	public void testSignalsDoNotLeakBetweenScripts() {
		LoopController.signalBreak(firstScript);
		assertFalse(LoopController.consumeBreak(secondScript));
		assertTrue(LoopController.consumeBreak(firstScript));
	}

	@Test
	public void testContinueSignalDoesNotActAsBreak() {
		LoopController.signalContinue(firstScript);
		assertFalse(LoopController.consumeBreak(firstScript));
		assertTrue(LoopController.consumeContinue(firstScript));
	}

	@Test
	public void testBreakOverwritesContinue() {
		LoopController.signalContinue(firstScript);
		LoopController.signalBreak(firstScript);
		assertFalse(LoopController.consumeContinue(firstScript));
		assertTrue(LoopController.consumeBreak(firstScript));
	}

	@Test
	public void testNullScriptSignalsAreIgnored() {
		LoopController.signalBreak(null);
		LoopController.signalContinue(null);
		assertFalse(LoopController.consumeBreak(null));
		assertFalse(LoopController.consumeContinue(null));
	}
}
