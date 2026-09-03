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
package org.catrobat.catroid.ui.scene;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.R;
import org.catrobat.catroid.content.Scene;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.ui.SpriteActivity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Engine-style scene editor: shows the current scene with all sprites on it.
 * Tap a sprite to open its code/sprite/sounds editor, drag to reposition.
 */
public class SceneFragment extends Fragment implements SceneView.OnSpriteClickListener {

	public static final String TAG = SceneFragment.class.getSimpleName();

	private SceneView sceneView;
	private TextView sceneNameView;
	private TextView hintView;

	public SceneFragment() {
		// required empty constructor
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_scene, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		sceneView = view.findViewById(R.id.scene_view);
		sceneNameView = view.findViewById(R.id.scene_name_label);
		hintView = view.findViewById(R.id.scene_hint);
		sceneView.setOnSpriteClickListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		refresh();
	}

	/**
	 * Redraws the scene (call after sprites were added/removed/moved).
	 */
	public void refresh() {
		if (sceneView == null) {
			return;
		}
		Scene scene = ProjectManager.getInstance().getCurrentlyEditedScene();
		sceneView.setScene(scene);
		if (scene != null && sceneNameView != null) {
			sceneNameView.setText(scene.getName());
		}
		if (hintView != null) {
			boolean hasSprites = scene != null && scene.getSpriteList() != null
					&& scene.getSpriteList().size() > 1;
			hintView.setVisibility(hasSprites ? View.GONE : View.VISIBLE);
		}
	}

	@Override
	public void onSpriteClicked(Sprite sprite) {
		openSprite(sprite);
	}

	@Override
	public void onBackgroundClicked() {
		Scene scene = ProjectManager.getInstance().getCurrentlyEditedScene();
		if (scene != null) {
			Sprite background = scene.getBackgroundSprite();
			if (background != null) {
				openSprite(background);
			}
		}
	}

	private void openSprite(Sprite sprite) {
		if (sprite == null || getActivity() == null) {
			return;
		}
		Scene scene = ProjectManager.getInstance().getCurrentlyEditedScene();
		if (scene != null) {
			ProjectManager.getInstance().setCurrentSceneAndSprite(scene.getName(), sprite.getName());
		}
		startActivity(new Intent(getActivity(), SpriteActivity.class));
	}
}
