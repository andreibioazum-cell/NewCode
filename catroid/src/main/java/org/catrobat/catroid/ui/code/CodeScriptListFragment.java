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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.R;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.content.Script;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.io.asynctask.ProjectSaver;
import org.catrobat.catroid.ui.BottomBar;
import org.catrobat.catroid.ui.SpriteActivity;
import org.catrobat.catroid.utils.ToastUtil;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.ListFragment;

/**
 * The "Code" tab of the sprite editor: a list of code scripts of the current sprite.
 */
public class CodeScriptListFragment extends ListFragment {

	public static final String TAG = CodeScriptListFragment.class.getSimpleName();

	private CodeAdapter adapter;
	private Script undoScript;
	private int undoPosition;

	public CodeScriptListFragment() {
		// required empty constructor
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_code_scripts, container, false);
	}

	@Override
	public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public void onActivityCreated(@Nullable Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		adapter = new CodeAdapter();
		setListAdapter(adapter);
		refresh();
	}

	@Override
	public void onResume() {
		super.onResume();
		BottomBar.showBottomBar(getActivity());
		BottomBar.showPlayButton(getActivity());
		BottomBar.showAddButton(getActivity());
		refresh();
	}

	@Override
	public void onPause() {
		super.onPause();
		Project project = ProjectManager.getInstance().getCurrentProject();
		if (project != null && getContext() != null) {
			new ProjectSaver(project, getContext()).saveProjectAsync();
		}
	}

	public void refresh() {
		if (adapter == null) {
			return;
		}
		adapter.setScripts(ProjectManager.getInstance().getCurrentSprite());
	}

	@Override
	public void onListItemClick(android.widget.ListView l, View view, int position, long id) {
		List<Script> scripts = currentScripts();
		if (position < 0 || position >= scripts.size()) {
			return;
		}
		Script script = scripts.get(position);
		Intent intent = new Intent(getActivity(), CodeEditorActivity.class);
		intent.putExtra(CodeEditorActivity.EXTRA_SCENE_NAME,
				ProjectManager.getInstance().getCurrentlyEditedScene().getName());
		intent.putExtra(CodeEditorActivity.EXTRA_SPRITE_NAME,
				ProjectManager.getInstance().getCurrentSprite().getName());
		intent.putExtra(CodeEditorActivity.EXTRA_SCRIPT_ID, script.getScriptId().toString());
		startActivity(intent);
	}

	@Override
	public boolean onListItemLongClick(android.widget.ListView l, View view, int position, long id) {
		List<Script> scripts = currentScripts();
		if (position < 0 || position >= scripts.size()) {
			return false;
		}
		final Script script = scripts.get(position);
		new AlertDialog.Builder(getContext())
				.setTitle(CodeEvents.eventTitle(script, getContext()))
				.setItems(new CharSequence[] {
						getString(R.string.copy),
						getString(R.string.delete)
				}, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				Sprite sprite = ProjectManager.getInstance().getCurrentSprite();
				if (which == 0) {
					try {
						sprite.addScript((Script) script.clone());
						undoScript = null;
					} catch (CloneNotSupportedException exception) {
						ToastUtil.showError(getActivity(), R.string.error_copying_brick);
					}
				} else {
					undoScript = script;
					undoPosition = position;
					sprite.removeScript(script);
					SpriteActivity activity = (SpriteActivity) getActivity();
					activity.setUndoMenuItemVisibility(true);
					activity.showUndo(true);
				}
				refresh();
			}
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menu_undo) {
			if (undoScript != null) {
				Sprite sprite = ProjectManager.getInstance().getCurrentSprite();
				int position = Math.min(undoPosition, sprite.getScriptList().size());
				sprite.getScriptList().add(position, undoScript);
				undoScript = null;
				refresh();
			}
			SpriteActivity activity = (SpriteActivity) getActivity();
			activity.setUndoMenuItemVisibility(false);
			activity.showUndo(false);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private List<Script> currentScripts() {
		Sprite sprite = ProjectManager.getInstance().getCurrentSprite();
		if (sprite == null) {
			return new ArrayList<>();
		}
		return sprite.getScriptList();
	}

	private final class CodeAdapter extends ArrayAdapter<Script> {

		private List<Script> scripts = new ArrayList<>();

		CodeAdapter() {
			super(getActivity(), R.layout.item_code_script);
		}

		void setScripts(Sprite sprite) {
			scripts = sprite == null ? new ArrayList<>() : sprite.getScriptList();
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return scripts.size();
		}

		@Override
		public Script getItem(int position) {
			return scripts.get(position);
		}

		@Override
		public long getItemId(int position) {
			return scripts.get(position).getScriptId().hashCode();
		}

		@NonNull
		@Override
		public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
			View view = convertView;
			if (view == null) {
				view = LayoutInflater.from(getContext()).inflate(R.layout.item_code_script, parent, false);
			}
			Script script = scripts.get(position);
			TextView titleView = view.findViewById(R.id.code_script_title);
			TextView previewView = view.findViewById(R.id.code_script_preview);
			titleView.setText(CodeEvents.eventTitle(script, getContext()));
			String preview = firstLine(CodeGenerator.generate(script));
			if (preview.isEmpty()) {
				previewView.setText(R.string.code_script_empty);
			} else {
				previewView.setText(preview);
			}
			return view;
		}
	}

	private static String firstLine(String code) {
		for (String line : code.split("\n")) {
			String trimmed = line.trim();
			if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
				return trimmed;
			}
		}
		return "";
	}
}
