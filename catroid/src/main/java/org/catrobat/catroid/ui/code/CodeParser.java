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

import org.catrobat.catroid.common.SoundInfo;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.content.Sprite;
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
import org.catrobat.catroid.content.bricks.WaitBrick;
import org.catrobat.catroid.content.bricks.WhileBrick;
import org.catrobat.catroid.content.bricks.AskBrick;
import org.catrobat.catroid.formulaeditor.Formula;
import org.catrobat.catroid.formulaeditor.FormulaElement;
import org.catrobat.catroid.formulaeditor.Functions;
import org.catrobat.catroid.formulaeditor.Operators;
import org.catrobat.catroid.formulaeditor.UserVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the NewCode text language (a small Java-like language) into
 * {@link Brick} objects of the engine runtime.
 *
 * <p>Example:
 * <pre>
 * move(10);
 * if (score &gt; 5) {
 *     say("You win!");
 * } else {
 *     say("Try again");
 * }
 * </pre>
 */
public final class CodeParser {

	public static final class Result {
		public final List<Brick> bricks = new ArrayList<>();
		public final List<String> errors = new ArrayList<>();

		public boolean hasErrors() {
			return !errors.isEmpty();
		}
	}

	private static final Map<String, Functions> FUNCTIONS = new HashMap<>();
	private static final Map<String, Integer> FUNCTION_ARITY = new HashMap<>();

	static {
		registerFunction("abs", Functions.ABS, 1);
		registerFunction("sqrt", Functions.SQRT, 1);
		registerFunction("round", Functions.ROUND, 1);
		registerFunction("floor", Functions.FLOOR, 1);
		registerFunction("ceil", Functions.CEIL, 1);
		registerFunction("sin", Functions.SIN, 1);
		registerFunction("cos", Functions.COS, 1);
		registerFunction("tan", Functions.TAN, 1);
		registerFunction("atan2", Functions.ARCTAN2, 2);
		registerFunction("random", Functions.RAND, 2);
		registerFunction("min", Functions.MIN, 2);
		registerFunction("max", Functions.MAX, 2);
		registerFunction("pow", Functions.POWER, 2);
	}

	private static void registerFunction(String name, Functions function, int arity) {
		FUNCTIONS.put(name, function);
		FUNCTION_ARITY.put(name, arity);
	}

	private CodeParser() {
	}

	/**
	 * Parses the code of one script (the statements between the event header and the end).
	 */
	public static Result parse(String code, Project project, Sprite sprite) {
		Result result = new Result();
		if (code == null) {
			return result;
		}
		try {
			Parser parser = new Parser(code, project, sprite);
			parser.parseProgram(result.bricks);
		} catch (ParseException exception) {
			result.errors.add(exception.getMessage());
		} catch (StackOverflowError error) {
			result.errors.add("Code is too deep.");
		}
		return result;
	}

	public static final class ParseException extends Exception {
		private static final long serialVersionUID = 1L;

		public ParseException(String message) {
			super(message);
		}
	}

	private enum TokenType {
		IDENT, NUMBER, STRING, SYMBOL, EOF
	}

	private static final class Token {
		final TokenType type;
		final String text;
		final int line;

		Token(TokenType type, String text, int line) {
			this.type = type;
			this.text = text;
			this.line = line;
		}
	}

	/**
	 * Tokenizer with line tracking.
	 */
	private static final class Lexer {
		private final String source;
		private int position = 0;
		private int line = 1;

		Lexer(String source) {
			this.source = source;
		}

		List<Token> tokenize() {
			List<Token> tokens = new ArrayList<>();
			while (position < source.length()) {
				char c = source.charAt(position);
				if (c == '\n') {
					line++;
					position++;
					continue;
				}
				if (Character.isWhitespace(c)) {
					position++;
					continue;
				}
				if (c == '/' && position + 1 < source.length() && source.charAt(position + 1) == '/') {
					while (position < source.length() && source.charAt(position) != '\n') {
						position++;
					}
					continue;
				}
				if (c == '/' && position + 1 < source.length() && source.charAt(position + 1) == '*') {
					position += 2;
					while (position + 1 < source.length()
							&& !(source.charAt(position) == '*' && source.charAt(position + 1) == '/')) {
						if (source.charAt(position) == '\n') {
							line++;
						}
						position++;
					}
					if (position + 1 < source.length() && source.charAt(position) == '*'
							&& source.charAt(position + 1) == '/') {
						position += 2; // closing */
					} else {
						position = source.length(); // unterminated comment
					}
					continue;
				}
				if (c == '"') {
					tokens.add(lexString());
					continue;
				}
				if (Character.isDigit(c) || (c == '.' && position + 1 < source.length()
						&& Character.isDigit(source.charAt(position + 1)))) {
					tokens.add(lexNumber());
					continue;
				}
				if (Character.isJavaIdentifierStart(c)) {
					tokens.add(lexIdentifier());
					continue;
				}
				tokens.add(lexSymbol());
			}
			tokens.add(new Token(TokenType.EOF, "", line));
			return tokens;
		}

		private Token lexString() {
			int startLine = line;
			position++; // skip opening quote
			StringBuilder builder = new StringBuilder();
			boolean closed = false;
			while (position < source.length()) {
				char c = source.charAt(position);
				if (c == '\\' && position + 1 < source.length()) {
					char next = source.charAt(position + 1);
					switch (next) {
						case 'n':
							builder.append('\n');
							break;
						case 't':
							builder.append('\t');
							break;
						case '"':
							builder.append('"');
							break;
						case '\\':
							builder.append('\\');
							break;
						default:
							builder.append(next);
					}
					position += 2;
					continue;
				}
				if (c == '"') {
					closed = true;
					position++;
					break;
				}
				if (c == '\n') {
					break;
				}
				builder.append(c);
				position++;
			}
			if (!closed) {
				throw new IllegalStateException("Unterminated string on line " + startLine);
			}
			return new Token(TokenType.STRING, builder.toString(), startLine);
		}

		private Token lexNumber() {
			int startLine = line;
			int start = position;
			boolean dot = false;
			while (position < source.length()) {
				char c = source.charAt(position);
				if (Character.isDigit(c)) {
					position++;
				} else if (c == '.' && !dot) {
					dot = true;
					position++;
				} else {
					break;
				}
			}
			return new Token(TokenType.NUMBER, source.substring(start, position), startLine);
		}

		private Token lexIdentifier() {
			int startLine = line;
			int start = position;
			while (position < source.length() && Character.isJavaIdentifierPart(source.charAt(position))) {
				position++;
			}
			return new Token(TokenType.IDENT, source.substring(start, position), startLine);
		}

		private Token lexSymbol() {
			int startLine = line;
			int start = position;
			char c = source.charAt(position);
			String[] twoChar = {"==", "!=", "<=", ">=", "**"};
			for (String candidate : twoChar) {
				if (source.startsWith(candidate, position)) {
					position += 2;
					return new Token(TokenType.SYMBOL, candidate, startLine);
				}
			}
			position++;
			return new Token(TokenType.SYMBOL, String.valueOf(c), startLine);
		}
	}

	/**
	 * Recursive descent parser.
	 */
	private static final class Parser {
		private final List<Token> tokens;
		private final Project project;
		private final Sprite sprite;
		private int index = 0;

		Parser(String code, Project project, Sprite sprite) {
			List<Token> lexed;
			try {
				lexed = new Lexer(code).tokenize();
			} catch (IllegalStateException exception) {
				throw new ParseException(exception.getMessage());
			}
			this.tokens = lexed;
			this.project = project;
			this.sprite = sprite;
		}

		private Token current() {
			return tokens.get(index);
		}

		private Token next() {
			Token token = tokens.get(index);
			if (index < tokens.size() - 1) {
				index++;
			}
			return token;
		}

		private boolean check(String symbol) {
			return current().type == TokenType.SYMBOL && symbol.equals(current().text);
		}

		private boolean checkIdent(String ident) {
			return current().type == TokenType.IDENT && ident.equals(current().text);
		}

		private Token expect(String symbol) throws ParseException {
			Token token = current();
			if (token.type != TokenType.SYMBOL || !symbol.equals(token.text)) {
				throw new ParseException("Line " + token.line + ": expected '" + symbol + "' but found '"
						+ describe(token) + "'");
			}
			return next();
		}

		private Token expectIdent(String ident) throws ParseException {
			Token token = current();
			if (token.type != TokenType.IDENT || !ident.equals(token.text)) {
				throw new ParseException("Line " + token.line + ": expected '" + ident + "' but found '"
						+ describe(token) + "'");
			}
			return next();
		}

		private String describe(Token token) {
			if (token.type == TokenType.EOF) {
				return "end of code";
			}
			return token.text;
		}

		// ------------------------------------------------------------------
		// Program / statements
		// ------------------------------------------------------------------

		void parseProgram(List<Brick> out) throws ParseException {
			while (current().type != TokenType.EOF) {
				parseStatement(out);
			}
		}

		private void parseStatement(List<Brick> out) throws ParseException {
			Token token = current();
			if (token.type == TokenType.SYMBOL) {
				if (";".equals(token.text)) {
					next();
					return;
				}
				throw new ParseException("Line " + token.line + ": unexpected '" + token.text + "'");
			}
			if (token.type != TokenType.IDENT) {
				throw new ParseException("Line " + token.line + ": unexpected '" + describe(token) + "'");
			}
			if (checkIdent("if")) {
				out.add(parseIf());
			} else if (checkIdent("while")) {
				out.add(parseWhile());
			} else if (checkIdent("do")) {
				out.add(parseDoWhile());
			} else if (checkIdent("forever")) {
				out.add(parseForever());
			} else if (checkIdent("repeat")) {
				out.add(parseRepeat());
			} else {
				out.add(parseCallStatement(token.text));
			}
			if (check(";")) {
				next();
			}
		}

		private IfLogicBeginBrick parseIf() throws ParseException {
			expectIdent("if");
			expect("(");
			Formula condition = parseExpression();
			expect(")");
			IfLogicBeginBrick brick = new IfLogicBeginBrick(condition);
			expect("{");
			parseBlock(brick.getNestedBricks());
			expect("}");
			if (checkIdent("else")) {
				next();
				if (checkIdent("if")) {
					brick.getSecondaryNestedBricks().add(parseIf());
				} else {
					expect("{");
					parseBlock(brick.getSecondaryNestedBricks());
					expect("}");
				}
			}
			return brick;
		}

		private WhileBrick parseWhile() throws ParseException {
			expectIdent("while");
			expect("(");
			Formula condition = parseExpression();
			expect(")");
			WhileBrick brick = new WhileBrick();
			brick.setFormulaWithBrickField(Brick.BrickField.IF_CONDITION, condition);
			expect("{");
			parseBlock(brick.getNestedBricks());
			expect("}");
			return brick;
		}

		private DoWhileBrick parseDoWhile() throws ParseException {
			expectIdent("do");
			DoWhileBrick brick = new DoWhileBrick();
			expect("{");
			parseBlock(brick.getNestedBricks());
			expect("}");
			expectIdent("while");
			expect("(");
			Formula condition = parseExpression();
			expect(")");
			brick.setFormulaWithBrickField(Brick.BrickField.IF_CONDITION, condition);
			if (check(";")) {
				next();
			}
			return brick;
		}

		private ForeverBrick parseForever() throws ParseException {
			expectIdent("forever");
			ForeverBrick brick = new ForeverBrick();
			expect("{");
			parseBlock(brick.getNestedBricks());
			expect("}");
			return brick;
		}

		private RepeatBrick parseRepeat() throws ParseException {
			expectIdent("repeat");
			expect("(");
			Formula times = parseExpression();
			expect(")");
			RepeatBrick brick = new RepeatBrick(times);
			expect("{");
			parseBlock(brick.getNestedBricks());
			expect("}");
			return brick;
		}

		private void parseBlock(List<Brick> out) throws ParseException {
			while (current().type != TokenType.EOF) {
				if (check("}")) {
					return;
				}
				parseStatement(out);
			}
			throw new ParseException("Line " + current().line + ": expected '}' (end of code reached)");
		}

		private Brick parseCallStatement(String name) throws ParseException {
			int line = current().line;
			expectIdent(name);
			List<Formula> args = new ArrayList<>();
			expect("(");
			if (!check(")")) {
				args.add(parseExpression());
				while (check(",")) {
					next();
					args.add(parseExpression());
				}
			}
			expect(")");
			return makeCommand(name, args, line);
		}

		// ------------------------------------------------------------------
		// Commands
		// ------------------------------------------------------------------

		private Brick makeCommand(String name, List<Formula> args, int line) throws ParseException {
			switch (name) {
				case "move":
					ensureArgs(name, args, 1, line);
					return new MoveNStepsBrick(args.get(0));
				case "turn":
					ensureArgs(name, args, 1, line);
					return new TurnRightBrick(args.get(0));
				case "turn_left":
					ensureArgs(name, args, 1, line);
					return new TurnLeftBrick(args.get(0));
				case "point":
					ensureArgs(name, args, 1, line);
					return new PointInDirectionBrick(args.get(0));
				case "change_x":
					ensureArgs(name, args, 1, line);
					return new ChangeXByNBrick(args.get(0));
				case "change_y":
					ensureArgs(name, args, 1, line);
					return new ChangeYByNBrick(args.get(0));
				case "set_x":
					ensureArgs(name, args, 1, line);
					return new SetXBrick(args.get(0));
				case "set_y":
					ensureArgs(name, args, 1, line);
					return new SetYBrick(args.get(0));
				case "place":
					ensureArgs(name, args, 2, line);
					return new PlaceAtBrick(args.get(0), args.get(1));
				case "glide":
					ensureArgs(name, args, 3, line);
					return new GlideToBrick(args.get(0), args.get(1), args.get(2));
				case "set_size":
					ensureArgs(name, args, 1, line);
					return new SetSizeToBrick(args.get(0));
				case "change_size":
					ensureArgs(name, args, 1, line);
					return new ChangeSizeByNBrick(args.get(0));
				case "say":
					return makeSay(args, line);
				case "think":
					return makeThink(args, line);
				case "hide_text":
					ensureArgs(name, args, 0, line);
					return new HideTextBrick();
				case "hide":
					ensureArgs(name, args, 0, line);
					return new HideBrick();
				case "show":
					ensureArgs(name, args, 0, line);
					return new ShowBrick();
				case "next_look":
					ensureArgs(name, args, 0, line);
					return new NextLookBrick();
				case "delete":
					ensureArgs(name, args, 0, line);
					return new DeleteThisCloneBrick();
				case "clone":
					ensureArgs(name, args, 0, line);
					return new CloneBrick();
				case "come_to_front":
					ensureArgs(name, args, 0, line);
					return new ComeToFrontBrick();
				case "bounce_on_edge":
					ensureArgs(name, args, 0, line);
					return new IfOnEdgeBounceBrick();
				case "wait":
					ensureArgs(name, args, 1, line);
					return new WaitBrick(args.get(0));
				case "stop":
					ensureArgs(name, args, 0, line);
					return new StopScriptBrick(0);
				case "exit_stage":
					ensureArgs(name, args, 0, line);
					return new ExitStageBrick();
				case "broadcast":
					return makeBroadcast(name, args, line);
				case "wait_broadcast":
					return makeBroadcastWait(name, args, line);
				case "play_sound":
					return makePlaySound(name, args, line);
				case "stop_all_sounds":
					ensureArgs(name, args, 0, line);
					return new StopAllSoundsBrick();
				case "play_note":
					ensureArgs(name, args, 1, line);
					return new NoteBrick(args.get(0));
				case "set_variable":
					return makeSetVariable(name, args, line, false);
				case "change_variable":
					return makeSetVariable(name, args, line, true);
				case "read":
					return makeRead(name, args, line);
				default:
					throw new ParseException("Line " + line + ": unknown command '" + name + "'");
			}
		}

		private void ensureArgs(String name, List<Formula> args, int count, int line) throws ParseException {
			if (args.size() != count) {
				throw new ParseException("Line " + line + ": '" + name + "' expects " + count
						+ " argument" + (count == 1 ? "" : "s") + " but got " + args.size());
			}
		}

		private Brick makeSay(List<Formula> args, int line) throws ParseException {
			if (args.size() == 1) {
				String literal = stringLiteral(args.get(0));
				if (literal != null) {
					return new SayBubbleBrick(literal);
				}
				SayBubbleBrick brick = new SayBubbleBrick();
				brick.setFormulaWithBrickField(Brick.BrickField.STRING, args.get(0));
				return brick;
			}
			ensureArgs("say", args, 2, line);
			String literal = stringLiteral(args.get(0));
			if (literal != null) {
				SayForBubbleBrick brick = new SayForBubbleBrick(literal, 2f);
				brick.setFormulaWithBrickField(Brick.BrickField.DURATION_IN_SECONDS, args.get(1));
				return brick;
			}
			SayForBubbleBrick brick = new SayForBubbleBrick();
			brick.setFormulaWithBrickField(Brick.BrickField.STRING, args.get(0));
			brick.setFormulaWithBrickField(Brick.BrickField.DURATION_IN_SECONDS, args.get(1));
			return brick;
		}

		private Brick makeThink(List<Formula> args, int line) throws ParseException {
			ensureArgs("think", args, 1, line);
			String literal = stringLiteral(args.get(0));
			if (literal != null) {
				return new ThinkBubbleBrick(literal);
			}
			return new ThinkBubbleBrick(args.get(0));
		}

		private Brick makeBroadcast(String name, List<Formula> args, int line) throws ParseException {
			ensureArgs(name, args, 1, line);
			String message = stringLiteral(args.get(0));
			if (message == null) {
				throw new ParseException("Line " + line + ": '" + name + "' expects a string, e.g. "
						+ name + "(\"start\")");
			}
			return new BroadcastBrick(message);
		}

		private Brick makeBroadcastWait(String name, List<Formula> args, int line) throws ParseException {
			ensureArgs(name, args, 1, line);
			String message = stringLiteral(args.get(0));
			if (message == null) {
				throw new ParseException("Line " + line + ": '" + name + "' expects a string, e.g. "
						+ name + "(\"start\")");
			}
			return new BroadcastWaitBrick(message);
		}

		private Brick makePlaySound(String name, List<Formula> args, int line) throws ParseException {
			ensureArgs(name, args, 1, line);
			String soundName = stringLiteral(args.get(0));
			if (soundName == null) {
				throw new ParseException("Line " + line + ": '" + name + "' expects a string, e.g. "
						+ name + "(\"boom\")");
			}
			SoundInfo sound = null;
			for (SoundInfo candidate : sprite.getSoundList()) {
				if (soundName.equals(candidate.getName())) {
					sound = candidate;
					break;
				}
			}
			if (sound == null) {
				throw new ParseException("Line " + line + ": sound '" + soundName
						+ "' was not found on this sprite");
			}
			PlaySoundBrick brick = new PlaySoundBrick();
			brick.setSound(sound);
			return brick;
		}

		private Brick makeSetVariable(String name, List<Formula> args, int line, boolean change)
				throws ParseException {
			ensureArgs(name, args, 2, line);
			String variableName = identifier(args.get(0));
			if (variableName == null) {
				throw new ParseException("Line " + line + ": the first argument of '" + name
						+ "' must be a variable name, e.g. " + name + "(score, 10)");
			}
			UserVariable variable = findOrCreateVariable(variableName);
			if (change) {
				return new ChangeVariableBrick(args.get(1), variable);
			}
			return new SetVariableBrick(args.get(1), variable);
		}

		private Brick makeRead(String name, List<Formula> args, int line) throws ParseException {
			ensureArgs(name, args, 2, line);
			String variableName = identifier(args.get(0));
			if (variableName == null) {
				throw new ParseException("Line " + line + ": the first argument of '" + name
						+ "' must be a variable name, e.g. " + name + "(answer, \"Name?\")");
			}
			UserVariable variable = findOrCreateVariable(variableName);
			return new AskBrick(args.get(1), variable);
		}

		private UserVariable findOrCreateVariable(String name) {
			UserVariable variable = sprite.getUserVariable(name);
			if (variable == null && project != null) {
				variable = project.getUserVariable(name);
			}
			if (variable == null) {
				variable = new UserVariable(name);
				sprite.addUserVariable(variable);
			}
			return variable;
		}

		// ------------------------------------------------------------------
		// Expressions
		// ------------------------------------------------------------------

		private Formula parseExpression() throws ParseException {
			FormulaElement tree = parseOr();
			return new Formula(tree);
		}

		private FormulaElement parseOr() throws ParseException {
			FormulaElement left = parseAnd();
			while (checkIdent("or")) {
				int line = next().line;
				FormulaElement right = parseAnd();
				left = operatorElement(Operators.LOGICAL_OR, left, right, line);
			}
			return left;
		}

		private FormulaElement parseAnd() throws ParseException {
			FormulaElement left = parseNot();
			while (checkIdent("and")) {
				int line = next().line;
				FormulaElement right = parseNot();
				left = operatorElement(Operators.LOGICAL_AND, left, right, line);
			}
			return left;
		}

		private FormulaElement parseNot() throws ParseException {
			if (checkIdent("not")) {
				int line = next().line;
				FormulaElement operand = parseNot();
				return operatorElement(Operators.LOGICAL_NOT, null, operand, line);
			}
			return parseComparison();
		}

		private FormulaElement parseComparison() throws ParseException {
			FormulaElement left = parseAdditive();
			Token token = current();
			if (token.type == TokenType.SYMBOL) {
				Operators operator = null;
				if ("==".equals(token.text)) {
					operator = Operators.EQUAL;
				} else if ("!=".equals(token.text)) {
					operator = Operators.NOT_EQUAL;
				} else if ("<".equals(token.text)) {
					operator = Operators.SMALLER_THAN;
				} else if (">".equals(token.text)) {
					operator = Operators.GREATER_THAN;
				} else if ("<=".equals(token.text)) {
					operator = Operators.SMALLER_OR_EQUAL;
				} else if (">=".equals(token.text)) {
					operator = Operators.GREATER_OR_EQUAL;
				}
				if (operator != null) {
					next();
					FormulaElement right = parseAdditive();
					return operatorElement(operator, left, right, token.line);
				}
			}
			return left;
		}

		private FormulaElement parseAdditive() throws ParseException {
			FormulaElement left = parseMultiplicative();
			while (true) {
				Token token = current();
				Operators operator = null;
				if (token.type == TokenType.SYMBOL) {
					if ("+".equals(token.text)) {
						operator = Operators.PLUS;
					} else if ("-".equals(token.text)) {
						operator = Operators.MINUS;
					}
				}
				if (operator == null) {
					break;
				}
				next();
				FormulaElement right = parseMultiplicative();
				left = operatorElement(operator, left, right, token.line);
			}
			return left;
		}

		private FormulaElement parseMultiplicative() throws ParseException {
			FormulaElement left = parseUnary();
			while (true) {
				Token token = current();
				Operators operator = null;
				if (token.type == TokenType.SYMBOL) {
					if ("*".equals(token.text)) {
						operator = Operators.MULT;
					} else if ("/".equals(token.text)) {
						operator = Operators.DIVIDE;
					} else if ("%".equals(token.text)) {
						operator = Operators.MOD;
					} else if ("**".equals(token.text)) {
						operator = Operators.POW;
					}
				}
				if (operator == null) {
					break;
				}
				next();
				FormulaElement right = parseUnary();
				left = operatorElement(operator, left, right, token.line);
			}
			return left;
		}

		private FormulaElement parseUnary() throws ParseException {
			Token token = current();
			if (token.type == TokenType.SYMBOL && ("-".equals(token.text) || "+".equals(token.text))) {
				next();
				FormulaElement operand = parseUnary();
				if ("-".equals(token.text)) {
					return operatorElement(Operators.MINUS, null, operand, token.line);
				}
				return operand;
			}
			return parsePrimary();
		}

		private FormulaElement parsePrimary() throws ParseException {
			Token token = current();
			switch (token.type) {
				case NUMBER: {
					next();
					return new FormulaElement(FormulaElement.ElementType.NUMBER, token.text, null);
				}
				case STRING: {
					next();
					return new FormulaElement(FormulaElement.ElementType.STRING, token.text, null);
				}
				case SYMBOL: {
					if ("(".equals(token.text)) {
						next();
						FormulaElement inner = parseOr();
						expect(")");
						return new FormulaElement(FormulaElement.ElementType.BRACKET, null, null, null, inner);
					}
					throw new ParseException("Line " + token.line + ": unexpected '" + token.text + "'");
				}
				case IDENT: {
					if (checkIdent("true")) {
						next();
						return new FormulaElement(FormulaElement.ElementType.FUNCTION, Functions.TRUE.toString(),
								null);
					}
					if (checkIdent("false")) {
						next();
						return new FormulaElement(FormulaElement.ElementType.FUNCTION, Functions.FALSE.toString(),
								null);
					}
					Functions function = FUNCTIONS.get(token.text);
					if (function != null && peekIsSymbol("(")) {
						next();
						next(); // '('
						List<FormulaElement> args = new ArrayList<>();
						if (!check(")")) {
							args.add(parseOr());
							while (check(",")) {
								next();
								args.add(parseOr());
							}
						}
						expect(")");
						Integer arity = FUNCTION_ARITY.get(token.text);
						if (args.size() != arity) {
							throw new ParseException("Line " + token.line + ": function '" + token.text
									+ "' expects " + arity + " argument" + (arity == 1 ? "" : "s"));
						}
						FormulaElement functionTree = new FormulaElement(FormulaElement.ElementType.FUNCTION,
								token.text.toUpperCase(Locale.US), null);
						if (args.size() >= 1) {
							functionTree.setLeftChild(args.get(0));
						}
						if (args.size() >= 2) {
							functionTree.setRightChild(args.get(1));
						}
						return functionTree;
					}
					next();
					UserVariable variable = sprite.getUserVariable(token.text);
					if (variable == null && project != null) {
						variable = project.getUserVariable(token.text);
					}
					if (variable == null) {
						throw new ParseException("Line " + token.line + ": unknown variable '"
								+ token.text + "' (create it first with set_variable(" + token.text + ", 0))");
					}
					return new FormulaElement(FormulaElement.ElementType.USER_VARIABLE, token.text, null);
				}
				default:
					throw new ParseException("Line " + token.line + ": unexpected '" + describe(token) + "'");
			}
		}

		private boolean peekIsSymbol(String symbol) {
			if (index + 1 >= tokens.size()) {
				return false;
			}
			Token token = tokens.get(index + 1);
			return token.type == TokenType.SYMBOL && symbol.equals(token.text);
		}

		private FormulaElement operatorElement(Operators operator, FormulaElement left, FormulaElement right,
				int line) throws ParseException {
			FormulaElement element = new FormulaElement(FormulaElement.ElementType.OPERATOR,
					operator.toString(), null, left, right);
			if (left == null && right == null) {
				throw new ParseException("Line " + line + ": missing operand for operator");
			}
			return element;
		}

		/**
		 * Returns the literal string value of the formula if it is a plain string literal.
		 */
		private String stringLiteral(Formula formula) {
			FormulaElement tree = formula.getFormulaTree();
			if (tree == null || tree.getElementType() != FormulaElement.ElementType.STRING) {
				return null;
			}
			return tree.getValue();
		}

		/**
		 * Returns the variable name if the formula is a single user variable reference.
		 */
		private String identifier(Formula formula) {
			FormulaElement tree = formula.getFormulaTree();
			if (tree == null || tree.getElementType() != FormulaElement.ElementType.USER_VARIABLE) {
				return null;
			}
			return tree.getValue();
		}
	}
}
