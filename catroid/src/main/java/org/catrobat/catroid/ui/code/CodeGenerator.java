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
import org.catrobat.catroid.formulaeditor.InternToken;
import org.catrobat.catroid.formulaeditor.InternTokenType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

	/**
	 * FormulaElement's children are private in this fork, so the tree is read
	 * through its flat in-order intern token list and re-parsed into a small
	 * node structure that this class renders with the language's precedence.
	 */
	static String formulaToString(Formula formula) {
		if (formula == null) {
			return "?";
		}
		FormulaElement tree = formula.getFormulaTree();
		if (tree == null) {
			return "?";
		}
		FNode node = parseOrExpr(new TokenCursor(tree.getInternTokenList()));
		if (node == null) {
			return "?";
		}
		return renderNode(node, null, false);
	}

	private static final class FNode {
		final String kind; // literal, string, variable, unary, binary, call
		final String value; // literal text, name, operator or function name
		final FNode left;
		final FNode right;

		FNode(String kind, String value, FNode left, FNode right) {
			this.kind = kind;
			this.value = value;
			this.left = left;
			this.right = right;
		}
	}

	private static final class TokenCursor {
		private final List<InternToken> tokens;
		private int position = 0;

		TokenCursor(List<InternToken> tokens) {
			this.tokens = tokens;
		}

		InternToken peek() {
			return position < tokens.size() ? tokens.get(position) : null;
		}

		InternToken advance() {
			return tokens.get(position++);
		}

		boolean peekOperator(String name) {
			InternToken token = peek();
			return token != null && token.getInternTokenType() == InternTokenType.OPERATOR
					&& name.equals(token.getTokenStringValue());
		}
	}

	private static FNode parseOrExpr(TokenCursor cursor) {
		FNode left = parseAndExpr(cursor);
		while (cursor.peekOperator("LOGICAL_OR")) {
			cursor.advance();
			left = new FNode("binary", "LOGICAL_OR", left, parseAndExpr(cursor));
		}
		return left;
	}

	private static FNode parseAndExpr(TokenCursor cursor) {
		FNode left = parseNotExpr(cursor);
		while (cursor.peekOperator("LOGICAL_AND")) {
			cursor.advance();
			left = new FNode("binary", "LOGICAL_AND", left, parseNotExpr(cursor));
		}
		return left;
	}

	private static FNode parseNotExpr(TokenCursor cursor) {
		if (cursor.peekOperator("LOGICAL_NOT")) {
			cursor.advance();
			return new FNode("unary", "LOGICAL_NOT", null, parseNotExpr(cursor));
		}
		return parseComparisonExpr(cursor);
	}

	private static FNode parseComparisonExpr(TokenCursor cursor) {
		FNode left = parseAdditiveExpr(cursor);
		String op = comparisonOperator(cursor);
		if (op != null) {
			cursor.advance();
			return new FNode("binary", op, left, parseAdditiveExpr(cursor));
		}
		return left;
	}

	private static String comparisonOperator(TokenCursor cursor) {
		InternToken token = cursor.peek();
		if (token == null || token.getInternTokenType() != InternTokenType.OPERATOR) {
			return null;
		}
		String name = token.getTokenStringValue();
		if ("EQUAL".equals(name) || "NOT_EQUAL".equals(name) || "SMALLER_THAN".equals(name)
				|| "GREATER_THAN".equals(name) || "SMALLER_OR_EQUAL".equals(name)
				|| "GREATER_OR_EQUAL".equals(name)) {
			return name;
		}
		return null;
	}

	private static FNode parseAdditiveExpr(TokenCursor cursor) {
		FNode left = parseMultiplicativeExpr(cursor);
		while (cursor.peekOperator("PLUS") || cursor.peekOperator("MINUS")) {
			String op = cursor.peekOperator("PLUS") ? "PLUS" : "MINUS";
			cursor.advance();
			left = new FNode("binary", op, left, parseMultiplicativeExpr(cursor));
		}
		return left;
	}

	private static FNode parseMultiplicativeExpr(TokenCursor cursor) {
		FNode left = parseUnaryExpr(cursor);
		while (true) {
			String op = null;
			for (String candidate : new String[] {"MULT", "DIVIDE", "MOD", "POW"}) {
				if (cursor.peekOperator(candidate)) {
					op = candidate;
					break;
				}
			}
			if (op == null) {
				return left;
			}
			cursor.advance();
			left = new FNode("binary", op, left, parseUnaryExpr(cursor));
		}
	}

	private static FNode parseUnaryExpr(TokenCursor cursor) {
		if (cursor.peekOperator("MINUS")) {
			cursor.advance();
			return new FNode("unary", "MINUS", null, parseUnaryExpr(cursor));
		}
		return parsePrimaryExpr(cursor);
	}

	private static FNode parsePrimaryExpr(TokenCursor cursor) {
		InternToken token = cursor.peek();
		if (token == null) {
			return new FNode("literal", "0", null, null);
		}
		switch (token.getInternTokenType()) {
			case NUMBER:
				cursor.advance();
				return new FNode("literal", token.getTokenStringValue(), null, null);
			case STRING:
				cursor.advance();
				return new FNode("string", token.getTokenStringValue(), null, null);
			case USER_VARIABLE:
			case USER_LIST:
			case SENSOR:
			case USER_DEFINED_BRICK_INPUT:
			case COLLISION_FORMULA:
				cursor.advance();
				return new FNode("variable", token.getTokenStringValue(), null, null);
			case BRACKET_OPEN:
				cursor.advance();
				FNode inner = parseOrExpr(cursor);
				if (cursor.peek() != null
						&& cursor.peek().getInternTokenType() == InternTokenType.BRACKET_CLOSE) {
					cursor.advance();
				}
				return inner;
			case FUNCTION_NAME: {
				cursor.advance();
				String name = token.getTokenStringValue();
				if (cursor.peek() != null
						&& cursor.peek().getInternTokenType() == InternTokenType.FUNCTION_PARAMETERS_BRACKET_OPEN) {
					cursor.advance();
					FNode first = parseOrExpr(cursor);
					FNode second = null;
					while (cursor.peek() != null
							&& cursor.peek().getInternTokenType() == InternTokenType.FUNCTION_PARAMETER_DELIMITER) {
						cursor.advance();
						second = parseOrExpr(cursor);
					}
					if (cursor.peek() != null
							&& cursor.peek().getInternTokenType() == InternTokenType.FUNCTION_PARAMETERS_BRACKET_CLOSE) {
						cursor.advance();
					}
					return new FNode("call", name, first, second);
				}
				return new FNode("call", name, null, null);
			}
			default:
				cursor.advance();
				return new FNode("literal", "?", null, null);
		}
	}

	private static String renderNode(FNode node, String parentOp, boolean isRightChild) {
		if (node == null) {
			return "?";
		}
		switch (node.kind) {
			case "literal":
				return node.value == null || node.value.isEmpty() ? "0" : node.value;
			case "string":
				return quoted(node.value);
			case "variable":
				return node.value == null ? "?" : node.value;
			case "unary":
				if ("MINUS".equals(node.value)) {
					return "-" + renderNode(node.right, "MINUS", true);
				}
				if ("LOGICAL_NOT".equals(node.value)) {
					return "not " + renderNode(node.right, "NOT_PRECEDENT", true);
				}
				return "?";
			case "call": {
				String name = FUNCTION_NAMES.get(node.value);
				if (name == null) {
					name = node.value == null ? "?" : node.value.toLowerCase(Locale.US);
				}
				if (node.left == null) {
					return name;
				}
				if (node.right == null) {
					return name + "(" + renderNode(node.left, null, false) + ")";
				}
				return name + "(" + renderNode(node.left, null, false) + ", "
						+ renderNode(node.right, null, false) + ")";
			}
			case "binary": {
				String op = node.value;
				if ("POW".equals(op)) {
					return "pow(" + renderNode(node.left, null, false) + ", "
							+ renderNode(node.right, null, false) + ")";
				}
				String symbol;
				if ("PLUS".equals(op)) {
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
					symbol = op.toLowerCase(Locale.US);
				}
				String result = renderNode(node.left, op, false) + " " + symbol + " "
						+ renderNode(node.right, op, true);
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
