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

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.R;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.content.Scene;
import org.catrobat.catroid.content.Script;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.io.asynctask.ProjectSaver;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

/**
 * Full screen code editor for one script (the "engine" code view).
 */
public class CodeEditorActivity extends AppCompatActivity {

	public static final String EXTRA_SCENE_NAME = "sceneName";
	public static final String EXTRA_SPRITE_NAME = "spriteName";
	public static final String EXTRA_SCRIPT_ID = "scriptId";

	private static final Set<String> KEYWORDS = new HashSet<>();

	static {
		KEYWORDS.add("if");
		KEYWORDS.add("else");
		KEYWORDS.add("while");
		KEYWORDS.add("do");
		KEYWORDS.add("forever");
		KEYWORDS.add("repeat");
		KEYWORDS.add("and");
		KEYWORDS.add("or");
		KEYWORDS.add("not");
		KEYWORDS.add("true");
		KEYWORDS.add("false");
	}

	private EditText codeView;
	private TextView statusView;
	private Project project;
	private Sprite sprite;
	private Script script;
	private boolean dirty = false;
	private boolean highlighting = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_code_editor);

		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		ProjectManager projectManager = ProjectManager.getInstance();
		project = projectManager.getCurrentProject();

		String sceneName = getIntent().getStringExtra(EXTRA_SCENE_NAME);
		String spriteName = getIntent().getStringExtra(EXTRA_SPRITE_NAME);
		Scene scene = null;
		if (sceneName != null && project != null) {
			for (Scene candidate : project.getSceneList()) {
				if (sceneName.equals(candidate.getName())) {
					scene = candidate;
					break;
				}
			}
		}
		if (scene == null) {
			scene = projectManager.getCurrentlyEditedScene();
		}
		Sprite foundSprite = null;
		if (scene != null && spriteName != null) {
			foundSprite = scene.getSprite(spriteName);
		}
		if (foundSprite == null) {
			foundSprite = projectManager.getCurrentSprite();
		}
		sprite = foundSprite;
		if (sprite == null || project == null) {
			finish();
			return;
		}

		String scriptIdText = getIntent().getStringExtra(EXTRA_SCRIPT_ID);
		UUID scriptId = null;
		if (scriptIdText != null) {
			try {
				scriptId = UUID.fromString(scriptIdText);
			} catch (IllegalArgumentException ignored) {
			}
		}
		script = null;
		for (Script candidate : sprite.getScriptList()) {
			if (scriptId == null || scriptId.equals(candidate.getScriptId())) {
				script = candidate;
				break;
			}
		}
		if (script == null) {
			finish();
			return;
		}

		getSupportActionBar().setTitle(sprite.getName() + " / " + CodeEvents.eventTitle(script, this));

		codeView = findViewById(R.id.code_editor);
		statusView = findViewById(R.id.code_status);
		codeView.setTypeface(Typeface.MONOSPACE);
		codeView.setText(CodeGenerator.generate(script));

		codeView.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				dirty = true;
				if (!highlighting) {
					highlighting = true;
					try {
						CodeHighlighter.highlight(s, CodeEditorActivity.this);
					} finally {
						highlighting = false;
					}
				}
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_code_editor, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			saveAndFinish();
			return true;
		}
		if (item.getItemId() == R.id.code_help) {
			showHelpDialog();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		saveAndFinish();
	}

	private void saveAndFinish() {
		if (!save()) {
			return;
		}
		finish();
	}

	/**
	 * @return true if the code was parsed and applied
	 */
	private boolean save() {
		if (script == null || project == null) {
			return true;
		}
		String code = codeView.getText().toString();
		CodeParser.Result result = CodeParser.parse(code, project, sprite);
		if (result.hasErrors()) {
			showErrors(result.errors);
			return false;
		}
		script.getBrickList().clear();
		script.getBrickList().addAll(result.bricks);
		script.setParents();
		dirty = false;
		ProjectManager.getInstance().changedProject(project.getName());
		new ProjectSaver(project, getApplicationContext()).saveProjectAsync();
		statusView.setText(R.string.code_saved);
		statusView.setVisibility(View.VISIBLE);
		return true;
	}

	private void showErrors(List<String> errors) {
		StringBuilder builder = new StringBuilder();
		for (String error : errors) {
			builder.append(error).append('\n');
		}
		new AlertDialog.Builder(this)
				.setTitle(R.string.code_errors_title)
				.setMessage(builder.toString())
				.setPositiveButton(R.string.ok, null)
				.show();
	}

	private void showHelpDialog() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.code_help_title)
				.setMessage(R.string.code_help_reference)
				.setPositiveButton(R.string.ok, null)
				.show();
	}

	/**
	 * Lightweight single pass syntax highlighter for the NewCode language.
	 */
	static final class CodeHighlighter {

		private static final int NORMAL = 0;
		private static final int STRING = 1;
		private static final int LINE_COMMENT = 2;
		private static final int BLOCK_COMMENT = 3;

		static void highlight(Spannable text, android.content.Context context) {
			int keywordColor = ContextCompat.getColor(context, R.color.code_keyword);
			int stringColor = ContextCompat.getColor(context, R.color.code_string);
			int numberColor = ContextCompat.getColor(context, R.color.code_number);
			int commentColor = ContextCompat.getColor(context, R.color.code_comment);
			int functionColor = ContextCompat.getColor(context, R.color.code_function);

			int length = text.length();
			int state = NORMAL;
			int segmentStart = 0;
			int segmentMark = 0;
			int i = 0;
			while (i < length) {
				char c = text.charAt(i);
				if (state == NORMAL) {
					if (c == '"') {
						state = STRING;
						segmentMark = i;
						i++;
					} else if (c == '/' && i + 1 < length && text.charAt(i + 1) == '/') {
						state = LINE_COMMENT;
						segmentMark = i;
						i++;
					} else if (c == '/' && i + 1 < length && text.charAt(i + 1) == '*') {
						state = BLOCK_COMMENT;
						segmentMark = i;
						i += 2;
					} else if (Character.isDigit(c)) {
						int end = i;
						while (end < length && (Character.isDigit(text.charAt(end)) || text.charAt(end) == '.')) {
							end++;
						}
						text.setSpan(new ForegroundColorSpan(numberColor), i, end,
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
						segmentStart = end;
						segmentMark = end;
						i = end;
					} else if (Character.isJavaIdentifierStart(c)) {
						int end = i;
						while (end < length && Character.isJavaIdentifierPart(text.charAt(end))) {
							end++;
						}
						String word = text.subSequence(i, end).toString();
						int after = end;
						while (after < length && Character.isWhitespace(text.charAt(after))) {
							after++;
						}
						boolean call = after < length && text.charAt(after) == '(';
						if (KEYWORDS.contains(word)) {
							text.setSpan(new ForegroundColorSpan(keywordColor), i, end,
									Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
						} else if (call) {
							text.setSpan(new ForegroundColorSpan(functionColor), i, end,
									Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
						}
						segmentStart = end;
						segmentMark = end;
						i = end;
					} else {
						i++;
					}
				} else if (state == STRING) {
					if (c == '\\' && i + 1 < length) {
						i += 2;
					} else if (c == '"' || c == '\n') {
						text.setSpan(new ForegroundColorSpan(stringColor), segmentMark, i + (c == '"' ? 1 : 0),
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
						state = NORMAL;
						segmentStart = i + (c == '"' ? 1 : 0);
						segmentMark = segmentStart;
						i++;
					} else {
						i++;
					}
				} else if (state == LINE_COMMENT) {
					if (c == '\n') {
						text.setSpan(new ForegroundColorSpan(commentColor), segmentMark, i,
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
						state = NORMAL;
						segmentStart = i;
						segmentMark = i;
					}
					i++;
				} else {
					if (c == '*' && i + 1 < length && text.charAt(i + 1) == '/') {
						text.setSpan(new ForegroundColorSpan(commentColor), segmentMark, i + 2,
								Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
						state = NORMAL;
						segmentStart = i + 2;
						segmentMark = i + 2;
						i += 2;
					} else {
						i++;
					}
				}
			}
			if (state == STRING || state == LINE_COMMENT || state == BLOCK_COMMENT) {
				int color = state == STRING ? stringColor : commentColor;
				text.setSpan(new ForegroundColorSpan(color), segmentMark, length,
						Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
		}
	}
}
