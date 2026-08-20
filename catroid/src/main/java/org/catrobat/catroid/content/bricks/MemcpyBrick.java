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
package org.catrobat.catroid.content.bricks;

import org.catrobat.catroid.R;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.content.actions.ScriptSequenceAction;
import org.catrobat.catroid.formulaeditor.Formula;

public class MemcpyBrick extends FormulaBrick {

	private static final long serialVersionUID = 1L;

	public MemcpyBrick() {
		addAllowedBrickField(BrickField.C_DESTINATION, R.id.brick_c_memcpy_edit_text_destination);
		addAllowedBrickField(BrickField.C_SOURCE, R.id.brick_c_memcpy_edit_text_source);
		addAllowedBrickField(BrickField.C_SIZE, R.id.brick_c_memcpy_edit_text_size);
	}

	public MemcpyBrick(double destination, double source, double size) {
		this();
		setFormulaWithBrickField(BrickField.C_DESTINATION, new Formula(destination));
		setFormulaWithBrickField(BrickField.C_SOURCE, new Formula(source));
		setFormulaWithBrickField(BrickField.C_SIZE, new Formula(size));
	}

	@Override
	public BrickField getDefaultBrickField() {
		return BrickField.C_DESTINATION;
	}

	@Override
	public int getViewResource() {
		return R.layout.brick_c_memcpy;
	}

	@Override
	public void addActionToSequence(Sprite sprite, ScriptSequenceAction sequence) {
		sequence.addAction(sprite.getActionFactory().createMemcpyAction(sprite, sequence,
				getFormulaWithBrickField(BrickField.C_DESTINATION),
				getFormulaWithBrickField(BrickField.C_SOURCE),
				getFormulaWithBrickField(BrickField.C_SIZE)));
	}
}
