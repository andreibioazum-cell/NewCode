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

public class TypedefBrick extends FormulaBrick {

	private static final long serialVersionUID = 1L;

	public TypedefBrick() {
		addAllowedBrickField(BrickField.C_NAME, R.id.brick_c_typedef_edit_text_name);
		addAllowedBrickField(BrickField.C_BASE_TYPE, R.id.brick_c_typedef_edit_text_base_type);
	}

	public TypedefBrick(String name, String baseType) {
		this();
		setFormulaWithBrickField(BrickField.C_NAME, new Formula(name));
		setFormulaWithBrickField(BrickField.C_BASE_TYPE, new Formula(baseType));
	}

	@Override
	public BrickField getDefaultBrickField() {
		return BrickField.C_NAME;
	}

	@Override
	public int getViewResource() {
		return R.layout.brick_c_typedef;
	}

	@Override
	public void addActionToSequence(Sprite sprite, ScriptSequenceAction sequence) {
		sequence.addAction(sprite.getActionFactory().createTypedefAction(sprite, sequence,
				getFormulaWithBrickField(BrickField.C_NAME),
				getFormulaWithBrickField(BrickField.C_BASE_TYPE)));
	}
}
