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

public class PointerSetBrick extends FormulaBrick {

	private static final long serialVersionUID = 1L;

	public PointerSetBrick() {
		addAllowedBrickField(BrickField.C_POINTER, R.id.brick_c_pointer_set_edit_text_pointer);
		addAllowedBrickField(BrickField.C_OFFSET, R.id.brick_c_pointer_set_edit_text_offset);
		addAllowedBrickField(BrickField.C_VALUE, R.id.brick_c_pointer_set_edit_text_value);
		addAllowedBrickField(BrickField.C_TYPE, R.id.brick_c_pointer_set_edit_text_type);
	}

	public PointerSetBrick(double pointer, double offset, double value, String type) {
		this();
		setFormulaWithBrickField(BrickField.C_POINTER, new Formula(pointer));
		setFormulaWithBrickField(BrickField.C_OFFSET, new Formula(offset));
		setFormulaWithBrickField(BrickField.C_VALUE, new Formula(value));
		setFormulaWithBrickField(BrickField.C_TYPE, new Formula(type));
	}

	@Override
	public BrickField getDefaultBrickField() {
		return BrickField.C_POINTER;
	}

	@Override
	public int getViewResource() {
		return R.layout.brick_c_pointer_set;
	}

	@Override
	public void addActionToSequence(Sprite sprite, ScriptSequenceAction sequence) {
		sequence.addAction(sprite.getActionFactory().createPointerSetAction(sprite, sequence,
				getFormulaWithBrickField(BrickField.C_POINTER),
				getFormulaWithBrickField(BrickField.C_OFFSET),
				getFormulaWithBrickField(BrickField.C_VALUE),
				getFormulaWithBrickField(BrickField.C_TYPE)));
	}
}
