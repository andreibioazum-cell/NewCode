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
import org.catrobat.catroid.formulaeditor.UserVariable;

public class PointerGetBrick extends UserVariableBrickWithFormula {

	private static final long serialVersionUID = 1L;

	public PointerGetBrick() {
		addAllowedBrickField(BrickField.C_POINTER, R.id.brick_c_pointer_get_edit_text_pointer);
		addAllowedBrickField(BrickField.C_OFFSET, R.id.brick_c_pointer_get_edit_text_offset);
		addAllowedBrickField(BrickField.C_TYPE, R.id.brick_c_pointer_get_edit_text_type);
	}

	public PointerGetBrick(double pointer, double offset, String type, UserVariable userVariable) {
		this();
		setFormulaWithBrickField(BrickField.C_POINTER, new Formula(pointer));
		setFormulaWithBrickField(BrickField.C_OFFSET, new Formula(offset));
		setFormulaWithBrickField(BrickField.C_TYPE, new Formula(type));
		this.userVariable = userVariable;
	}

	@Override
	public BrickField getDefaultBrickField() {
		return BrickField.C_POINTER;
	}

	@Override
	public int getViewResource() {
		return R.layout.brick_c_pointer_get;
	}

	@Override
	protected int getSpinnerId() {
		return R.id.brick_c_pointer_get_spinner;
	}

	@Override
	public void addActionToSequence(Sprite sprite, ScriptSequenceAction sequence) {
		sequence.addAction(sprite.getActionFactory().createPointerGetAction(sprite, sequence,
				getFormulaWithBrickField(BrickField.C_POINTER),
				getFormulaWithBrickField(BrickField.C_OFFSET),
				getFormulaWithBrickField(BrickField.C_TYPE), userVariable));
	}
}
