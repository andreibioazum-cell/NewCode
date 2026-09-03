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

import org.catrobat.catroid.content.Script;
import org.catrobat.catroid.content.bricks.AskBrick;
import org.catrobat.catroid.content.bricks.BroadcastBrick;
import org.catrobat.catroid.content.bricks.BroadcastWaitBrick;
import org.catrobat.catroid.content.bricks.Brick;
import org.catrobat.catroid.content.bricks.ChangeSizeByNBrick;
import org.catrobat.catroid.content.bricks.ChangeVariableBrick;
import org.catrobat.catroid.content.bricks.ChangeXByNBrick;
import org.catrobat.catroid.content.bricks.ChangeYByNBrick;
import org.catrobat.catroid.content.bricks.CloneBrick;
import org.catrobat.catroid.content.bricks.ComeToFrontBrick;
import org.catrobat.catroid.content.bricks.DeleteThisCloneBrick;
import org.catrobat.catroid.content.bricks.DoWhileBrick;
import org.catrobat.catroid.content.bricks.ExitStageBrick;
import org.catrobat.catroid.content.bricks.FormulaBrick;
import org.catrobat.catroid.content.bricks.ForeverBrick;
import org.catrobat.catroid.content.bricks.GlideToBrick;
import org.catrobat.catroid.content.bricks.HideBrick;
import org.catrobat.catroid.content.bricks.HideTextBrick;
import org.catrobat.catroid.content.bricks.IfLogicBeginBrick;
import org.catrobat.catroid.content.bricks.IfOnEdgeBounceBrick;
import org.catrobat.catroid.content.bricks.IfThenLogicBeginBrick;
import org.catrobat.catroid.content.bricks.MoveNStepsBrick;
import org.catrobat.catroid.content.bricks.NoteBrick;
import org.catrobat.catroid.content.bricks.NextLookBrick;
import org.catrobat.catroid.content.bricks.PlaceAtBrick;
import org.catrobat.catroid.content.bricks.PointInDirectionBrick;
import org.catrobat.catroid.content.bricks.PlaySoundBrick;
import org.catrobat.catroid.content.bricks.RepeatBrick;
import org.catrobat.catroid.content.bricks.SayBubbleBrick;
import org.catrobat.catroid.content.bricks.SayForBubbleBrick;
import org.catrobat.catroid.content.bricks.SetSizeToBrick;
import org.catrobat.catroid.content.bricks.SetVariableBrick;
import org.catrobat.catroid.content.bricks.SetXBrick;
import org.catrobat.catroid.content.bricks.SetYBrick;
import org.catrobat.catroid.content.bricks.ShowBrick;
import org.catrobat.catroid.content.bricks.StopAllSoundsBrick;
import org.catrobat.catroid.content.bricks.StopScriptBrick;
import org.catrobat.catroid.content.bricks.ThinkBubbleBrick;
import org.catrobat.catroid.content.bricks.TurnLeftBrick;
import org.catrobat.catroid.content.bricks.TurnRightBrick;
import org.catrobat.catroid.content.bricks.UserVariableBrick;
import org.catrobat.catroid.content.bricks.WaitBrick;
import org.catrobat.catroid.content.bricks.WhileBrick;
import org.catrobat.catroid.formulaeditor.Formula;
import org.catrobat.catroid.formulaeditor.FormulaElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the bricks of a script as NewCode text (the inverse of {@link CodeParser}).
 * Bricks that have no text command are kept as comments so they are visible.
 */
public final class CodeGenerator {

	private static final Map<String, String> FUNCTION_NAMES = new HashMap<>();

	static {
		FUNCTION_NAMES.put("ABS", "abs");
		FUNCTION_NAMES.put("SQRT", "sqrt");
		FUNCTION_NAMES.put("ROUND", "round");
		FUNCTION_NAMES.put("FLOOR", "floor");
		FUNCTION_NAMES.put("CEIL", "ceil");
		FUNCTION_NAMES.put("SIN", "sin");
		FUNCTION_NAMES.put("COS", "cos");
		FUNCTION_NAMES.put("TAN", "tan");
		FUNCTION_NAMES.put("ARCTAN2", "atan2");
		FUNCTION_NAMES.put("RAND", "random");
		FUNCTION_NAMES.put("MIN", "min");
		FUNCTION_NAMES.put("MAX", "max");
		FUNCTION_NAMES.put("POWER", "pow");
		FUNCTION_NAMES.put("TRUE", "true");
		FUNCTION_NAMES.put("FALSE", "false");
	}

	private CodeGenerator() {
	}

	/**
	 * Generates the code body (statements only) of the given script.
	 */
	public static String generate(Script script) {
		StringBuilder builder = new StringBuilder();
		StringBuilder indentation = new StringBuilder();
		generateBlocks(script.getBrickList(), builder, indentation);
		return builder.toString();
	}

	private static void generateBlocks(List<Brick> bricks, StringBuilder out, StringBuilder indentation) {
		for (Brick brick : bricks) {
			generateBrick(brick, out, indentation);
		}
	}

	private static void generateBrick(Brick brick, StringBuilder out, StringBuilder indentation) {
		out.append(indentation);
		if (brick instanceof IfLogicBeginBrick || brick instanceof IfThenLogicBeginBrick) {
			Brick conditionHolder = brick;
			Formula condition = formula(conditionHolder, Brick.BrickField.IF_CONDITION);
			out.append("if (").append(formulaToString(condition)).append(") {");
			newline(out);
			indent(out, indentation);
			generateBlocks(nested(brick), out, indentation);
			dedent(out, indentation);
			out.append(indentation);
			List<Brick> elseBranch = secondaryNested(brick);
			if (elseBranch != null && !elseBranch.isEmpty()) {
				out.append("} else {");
				newline(out);
				indent(out, indentation);
				generateBlocks(elseBranch, out, indentation);
				dedent(out, indentation);
				out.append(indentation);
			}
			out.append('}');
			newline(out);
			return;
		}
		if (brick instanceof RepeatBrick) {
			Formula times = formula(brick, Brick.BrickField.TIMES_TO_REPEAT);
			out.append("repeat (").append(formulaToString(times)).append(") {");
			newline(out);
			indent(out, indentation);
			generateBlocks(nested(brick), out, indentation);
			dedent(out, indentation);
			out.append(indentation).append('}');
			newline(out);
			return;
		}
		if (brick instanceof WhileBrick) {
			Formula condition = formula(brick, Brick.BrickField.IF_CONDITION);
			out.append("while (").append(formulaToString(condition)).append(") {");
			newline(out);
			indent(out, indentation);
			generateBlocks(nested(brick), out, indentation);
			dedent(out, indentation);
			out.append(indentation).append('}');
			newline(out);
			return;
		}
		if (brick instanceof DoWhileBrick) {
			Formula condition = formula(brick, Brick.BrickField.IF_CONDITION);
			out.append("do {");
			newline(out);
			indent(out, indentation);
			generateBlocks(nested(brick), out, indentation);
			dedent(out, indentation);
			out.append(indentation).append("} while (").append(formulaToString(condition)).append(");");
			newline(out);
			return;
		}
		if (brick instanceof ForeverBrick) {
			out.append("forever {");
			newline(out);
			indent(out, indentation);
			generateBlocks(nested(brick), out, indentation);
			dedent(out, indentation);
			out.append(indentation).append('}');
			newline(out);
			return;
		}
		String command = simpleCommand(brick);
		if (command != null) {
			out.append(command).append(';');
		} else {
			out.append("// [block: ").append(brick.getClass().getSimpleName()).append("]");
		}
		newline(out);
	}

	// ----------------------------------------------------------------------
	// Simple commands
	// ----------------------------------------------------------------------

	private static String simpleCommand(Brick brick) {
		if (brick instanceof MoveNStepsBrick) {
			return "move(" + f(brick, Brick.BrickField.STEPS) + ")";
		}
		if (brick instanceof TurnRightBrick) {
			return "turn(" + f(brick, Brick.BrickField.TURN_RIGHT_DEGREES) + ")";
		}
		if (brick instanceof TurnLeftBrick) {
			return "turn_left(" + f(brick, Brick.BrickField.TURN_LEFT_DEGREES) + ")";
		}
		if (brick instanceof PointInDirectionBrick) {
			return "point(" + f(brick, Brick.BrickField.DEGREES) + ")";
		}
		if (brick instanceof ChangeXByNBrick) {
			return "change_x(" + f(brick, Brick.BrickField.X_POSITION_CHANGE) + ")";
		}
		if (brick instanceof ChangeYByNBrick) {
			return "change_y(" + f(brick, Brick.BrickField.Y_POSITION_CHANGE) + ")";
		}
		if (brick instanceof SetXBrick) {
			return "set_x(" + f(brick, Brick.BrickField.X_POSITION) + ")";
		}
		if (brick instanceof SetYBrick) {
			return "set_y(" + f(brick, Brick.BrickField.Y_POSITION) + ")";
		}
		if (brick instanceof PlaceAtBrick) {
			return "place(" + f(brick, Brick.BrickField.X_POSITION) + ", "
					+ f(brick, Brick.BrickField.Y_POSITION) + ")";
		}
		if (brick instanceof GlideToBrick) {
			return "glide(" + f(brick, Brick.BrickField.X_DESTINATION) + ", "
					+ f(brick, Brick.BrickField.Y_DESTINATION) + ", "
					+ f(brick, Brick.BrickField.DURATION_IN_SECONDS) + ")";
		}
		if (brick instanceof SetSizeToBrick) {
			return "set_size(" + f(brick, Brick.BrickField.SIZE) + ")";
		}
		if (brick instanceof ChangeSizeByNBrick) {
			return "change_size(" + f(brick, Brick.BrickField.SIZE_CHANGE) + ")";
		}
		if (brick instanceof SayBubbleBrick) {
			return "say(" + f(brick, Brick.BrickField.STRING) + ")";
		}
		if (brick instanceof SayForBubbleBrick) {
			return "say(" + f(brick, Brick.BrickField.STRING) + ", "
					+ f(brick, Brick.BrickField.DURATION_IN_SECONDS) + ")";
		}
		if (brick instanceof ThinkBubbleBrick) {
			return "think(" + f(brick, Brick.BrickField.STRING) + ")";
		}
		if (brick instanceof HideTextBrick) {
			return "hide_text()";
		}
		if (brick instanceof HideBrick) {
			return "hide()";
		}
		if (brick instanceof ShowBrick) {
			return "show()";
		}
		if (brick instanceof NextLookBrick) {
			return "next_look()";
		}
		if (brick instanceof DeleteThisCloneBrick) {
			return "delete()";
		}
		if (brick instanceof CloneBrick) {
			return "clone()";
		}
		if (brick instanceof ComeToFrontBrick) {
			return "come_to_front()";
		}
		if (brick instanceof IfOnEdgeBounceBrick) {
			return "bounce_on_edge()";
		}
		if (brick instanceof WaitBrick) {
			return "wait(" + f(brick, Brick.BrickField.TIME_TO_WAIT_IN_SECONDS) + ")";
		}
		if (brick instanceof StopScriptBrick) {
			return "stop()";
		}
		if (brick instanceof ExitStageBrick) {
			return "exit_stage()";
		}
		if (brick instanceof BroadcastBrick) {
			return "broadcast(" + quoted(((BroadcastBrick) brick).getBroadcastMessage()) + ")";
		}
		if (brick instanceof BroadcastWaitBrick) {
			return "wait_broadcast(" + quoted(((BroadcastWaitBrick) brick).getBroadcastMessage()) + ")";
		}
		if (brick instanceof PlaySoundBrick) {
			if (((PlaySoundBrick) brick).getSound() != null) {
				return "play_sound(" + quoted(((PlaySoundBrick) brick).getSound().getName()) + ")";
			}
			return null;
		}
		if (brick instanceof StopAllSoundsBrick) {
			return "stop_all_sounds()";
		}
		if (brick instanceof NoteBrick) {
			return "play_note(" + f(brick, Brick.BrickField.NOTE) + ")";
		}
		if (brick instanceof SetVariableBrick) {
			return "set_variable(" + variableName(brick) + ", "
					+ f(brick, Brick.BrickField.VARIABLE) + ")";
		}
		if (brick instanceof ChangeVariableBrick) {
			return "change_variable(" + variableName(brick) + ", "
					+ f(brick, Brick.BrickField.VARIABLE_CHANGE) + ")";
		}
		if (brick instanceof AskBrick) {
			return "read(" + variableName(brick) + ", " + f(brick, Brick.BrickField.ASK_QUESTION) + ")";
		}
		return null;
	}

	private static String variableName(Brick brick) {
		if (brick instanceof UserVariableBrick && ((UserVariableBrick) brick).getUserVariable() != null) {
			return ((UserVariableBrick) brick).getUserVariable().getName();
		}
		return "name";
	}

	// ----------------------------------------------------------------------
	// Helpers
	// ----------------------------------------------------------------------

	private static List<Brick> nested(Brick brick) {
		if (brick instanceof IfLogicBeginBrick) {
			return ((IfLogicBeginBrick) brick).getNestedBricks();
		}
		if (brick instanceof IfThenLogicBeginBrick) {
			return ((IfThenLogicBeginBrick) brick).getNestedBricks();
		}
		if (brick instanceof RepeatBrick) {
			return ((RepeatBrick) brick).getNestedBricks();
		}
		if (brick instanceof WhileBrick) {
			return ((WhileBrick) brick).getNestedBricks();
		}
		if (brick instanceof DoWhileBrick) {
			return ((DoWhileBrick) brick).getNestedBricks();
		}
		if (brick instanceof ForeverBrick) {
			return ((ForeverBrick) brick).getNestedBricks();
		}
		return null;
	}

	private static List<Brick> secondaryNested(Brick brick) {
		if (brick instanceof IfLogicBeginBrick) {
			return ((IfLogicBeginBrick) brick).getSecondaryNestedBricks();
		}
		if (brick instanceof IfThenLogicBeginBrick) {
			return ((IfThenLogicBeginBrick) brick).getSecondaryNestedBricks();
		}
		return null;
	}

	private static Formula formula(Brick brick, Brick.BrickField field) {
		if (brick instanceof FormulaBrick) {
			try {
				return ((FormulaBrick) brick).getFormulaWithBrickField(field);
			} catch (IllegalArgumentException exception) {
				return null;
			}
		}
		return null;
	}

	private static String f(Brick brick, Brick.BrickField field) {
		return formulaToString(formula(brick, field));
	}

	private static String quoted(String value) {
		if (value == null) {
			return "\"\"";
		}
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static void newline(StringBuilder out) {
		out.append('\n');
	}

	private static void indent(StringBuilder out, StringBuilder indentation) {
		indentation.append("    ");
	}

	private static void dedent(StringBuilder out, StringBuilder indentation) {
		if (indentation.length() >= 4) {
			indentation.setLength(indentation.length() - 4);
		}
	}

	// ----------------------------------------------------------------------
	// Formula -> expression string
	// ----------------------------------------------------------------------

	static String formulaToString(Formula formula) {
		if (formula == null) {
			return "?";
		}
		FormulaElement tree = formula.getFormulaTree();
		if (tree == null) {
			return "?";
		}
		return elementToString(tree, null, false);
	}

	private static String elementToString(FormulaElement element, String parentOp, boolean isRightChild) {
		if (element == null) {
			return "?";
		}
		switch (element.getElementType()) {
			case NUMBER:
				return element.getValue() == null ? "0" : element.getValue();
			case STRING:
				return quoted(element.getValue());
			case USER_VARIABLE:
			case USER_LIST:
			case SENSOR:
			case USER_DEFINED_BRICK_INPUT:
			case COLLISION_FORMULA:
				return element.getValue() == null ? "?" : element.getValue();
			case BRACKET: {
				FormulaElement inner = element.getLeftChild() != null
						? element.getLeftChild() : element.getRightChild();
				return "(" + elementToString(inner, null, false) + ")";
			}
			case FUNCTION: {
				String name = FUNCTION_NAMES.get(element.getValue());
				if (name == null) {
					name = element.getValue() == null ? "?" : element.getValue().toLowerCase();
				}
				FormulaElement left = element.getLeftChild();
				FormulaElement right = element.getRightChild();
				if (left == null && right == null) {
					return name;
				}
				if (left != null && right == null) {
					return name + "(" + elementToString(left, null, false) + ")";
				}
				if (left != null && right != null) {
					return name + "(" + elementToString(left, null, false) + ", "
							+ elementToString(right, null, false) + ")";
				}
				return name + "(" + elementToString(right, null, false) + ")";
			}
			case OPERATOR: {
				String op = element.getValue();
				if (op == null) {
					return "?";
				}
				FormulaElement left = element.getLeftChild();
				FormulaElement right = element.getRightChild();
				if ("MINUS".equals(op) && left == null) {
					return "-" + elementToString(right, "MINUS", true);
				}
				if ("LOGICAL_NOT".equals(op) && left == null) {
					return "not " + elementToString(right, "NOT_PRECEDENT", true);
				}
				if (left == null || right == null) {
					return "?";
				}
				String symbol;
				if ("POWER".equals(op)) {
					return "pow(" + elementToString(left, null, false) + ", "
							+ elementToString(right, null, false) + ")";
				} else if ("PLUS".equals(op)) {
					symbol = "+";
				} else if ("MINUS".equals(op)) {
					symbol = "-";
				} else if ("MULT".equals(op)) {
					symbol = "*";
				} else if ("DIVIDE".equals(op)) {
					symbol = "/";
				} else if ("MOD".equals(op)) {
					symbol = "%";
				} else if ("EQUAL".equals(op)) {
					symbol = "==";
				} else if ("NOT_EQUAL".equals(op)) {
					symbol = "!=";
				} else if ("SMALLER_THAN".equals(op)) {
					symbol = "<";
				} else if ("GREATER_THAN".equals(op)) {
					symbol = ">";
				} else if ("SMALLER_OR_EQUAL".equals(op)) {
					symbol = "<=";
				} else if ("GREATER_OR_EQUAL".equals(op)) {
					symbol = ">=";
				} else if ("LOGICAL_AND".equals(op)) {
					symbol = "and";
				} else if ("LOGICAL_OR".equals(op)) {
					symbol = "or";
				} else {
					symbol = op.toLowerCase();
				}
				String leftStr = elementToString(left, op, false);
				String rightStr = elementToString(right, op, true);
				String result = leftStr + " " + symbol + " " + rightStr;
				if (needsParens(op, parentOp, isRightChild)) {
					return "(" + result + ")";
				}
				return result;
			}
			default:
				return "?";
		}
	}

	private static boolean needsParens(String op, String parentOp, boolean isRightChild) {
		if (parentOp == null) {
			return false;
		}
		// unary "not" binds tighter than "and"/"or": not (a and b) must keep its parens
		if ("NOT_PRECEDENT".equals(parentOp) && priorityValue(op) <= 2) {
			return true;
		}
		int childPriority = priorityValue(op);
		int parentPriority = priorityValue(parentOp);
		if (childPriority < parentPriority) {
			return true;
		}
		if (childPriority == parentPriority && isRightChild
				&& (isMinusLike(parentOp) || isComparison(parentOp))) {
			return true;
		}
		return false;
	}

	private static boolean isMinusLike(String op) {
		return "MINUS".equals(op) || "DIVIDE".equals(op) || "MOD".equals(op) || "POWER".equals(op);
	}

	private static boolean isComparison(String op) {
		return "EQUAL".equals(op) || "NOT_EQUAL".equals(op) || "SMALLER_THAN".equals(op)
				|| "GREATER_THAN".equals(op) || "SMALLER_OR_EQUAL".equals(op)
				|| "GREATER_OR_EQUAL".equals(op);
	}

	private static int priorityValue(String op) {
		if (op == null) {
			return 0;
		}
		switch (op) {
			case "POWER":
			case "MULT":
			case "DIVIDE":
			case "MOD":
				return 7;
			case "PLUS":
			case "MINUS":
				return 5;
			case "EQUAL":
			case "NOT_EQUAL":
			case "SMALLER_OR_EQUAL":
			case "GREATER_OR_EQUAL":
			case "SMALLER_THAN":
			case "GREATER_THAN":
				return 4;
			case "LOGICAL_AND":
				return 2;
			case "LOGICAL_OR":
				return 1;
			case "LOGICAL_NOT":
				return 4;
			default:
				return 0;
		}
	}
}
