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

public class TernaryBrick extends UserVariableBrickWithFormula {

	private static final long serialVersionUID = 1L;

	public TernaryBrick() {
		addAllowedBrickField(BrickField.TERNARY_CONDITION, R.id.brick_c_ternary_edit_text_condition);
		addAllowedBrickField(BrickField.TERNARY_IF_TRUE, R.id.brick_c_ternary_edit_text_if_true);
		addAllowedBrickField(BrickField.TERNARY_IF_FALSE, R.id.brick_c_ternary_edit_text_if_false);
	}

	public TernaryBrick(double condition, double ifTrue, double ifFalse, UserVariable userVariable) {
		this();
		setFormulaWithBrickField(BrickField.TERNARY_CONDITION, new Formula(condition));
		setFormulaWithBrickField(BrickField.TERNARY_IF_TRUE, new Formula(ifTrue));
		setFormulaWithBrickField(BrickField.TERNARY_IF_FALSE, new Formula(ifFalse));
		this.userVariable = userVariable;
	}

	@Override
	public BrickField getDefaultBrickField() {
		return BrickField.TERNARY_CONDITION;
	}

	@Override
	public int getViewResource() {
		return R.layout.brick_c_ternary;
	}

	@Override
	protected int getSpinnerId() {
		return R.id.brick_c_ternary_spinner;
	}

	@Override
	public void addActionToSequence(Sprite sprite, ScriptSequenceAction sequence) {
		sequence.addAction(sprite.getActionFactory().createTernaryAction(sprite, sequence,
				getFormulaWithBrickField(BrickField.TERNARY_CONDITION),
				getFormulaWithBrickField(BrickField.TERNARY_IF_TRUE),
				getFormulaWithBrickField(BrickField.TERNARY_IF_FALSE), userVariable));
	}
}
