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

package org.catrobat.catroid.stage;

import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.R;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.content.Scene;
import org.catrobat.catroid.content.Sprite;
import org.catrobat.catroid.content.bricks.Brick;
import org.catrobat.catroid.formulaeditor.SensorHandler;
import org.catrobat.catroid.formulaeditor.UserDataWrapper;
import org.catrobat.catroid.io.SoundManager;
import org.catrobat.catroid.io.StageAudioFocus;
import org.catrobat.catroid.pocketmusic.mididriver.MidiSoundManager;
import org.catrobat.catroid.ui.runtimepermissions.RequiresPermissionTask;

import java.util.List;

import static org.catrobat.catroid.stage.StageResourceHolder.getProjectsRuntimePermissionList;
import static org.catrobat.catroid.ui.runtimepermissions.RequiresPermissionTask.checkPermission;
import static org.koin.java.KoinJavaComponent.get;

public final class StageLifeCycleController {
	public static final String TAG = StageLifeCycleController.class.getSimpleName();

	private static final int REQUEST_PERMISSIONS_STAGE_RESOURCE_CREATE = 601;

	private StageLifeCycleController() {
		throw new AssertionError("no.");
	}

	static void stageCreate(final StageActivity stageActivity) {
		if (ProjectManager.getInstance().getCurrentProject() == null) {
			stageActivity.finish();
			Log.d(TAG, "no current project set, cowardly refusing to run");
			return;
		}

		StageActivity.numberOfSpritesCloned = 0;

		if (ProjectManager.getInstance().isCurrentProjectLandscapeMode()) {
			stageActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		} else {
			stageActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}

		UserDataWrapper.resetAllUserData(ProjectManager.getInstance().getCurrentProject());

		for (Scene scene : ProjectManager.getInstance().getCurrentProject().getSceneList()) {
			scene.firstStart = true;
		}

		stageActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		StageActivity.stageListener = new StageListener();
		stageActivity.brickDialogManager = new BrickDialogManager(stageActivity);
		stageActivity.calculateScreenSizes();

		stageActivity.configuration = new AndroidApplicationConfiguration();
		stageActivity.configuration.r = stageActivity.configuration.g = stageActivity.configuration.b = stageActivity.configuration.a = 8;
		stageActivity.initialize(StageActivity.stageListener, stageActivity.configuration);

		//CATROID-105 - TODO: does this make any difference? probably necessary for cast:
		if (stageActivity.getGdxGraphics().getView() instanceof SurfaceView) {
			SurfaceView glView = (SurfaceView) stageActivity.getGdxGraphics().getView();
			glView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
			glView.setZOrderMediaOverlay(true);
		}
		stageActivity.stageAudioFocus = new StageAudioFocus(stageActivity);
		stageActivity.stageResourceHolder = new StageResourceHolder(stageActivity);
		MidiSoundManager.getInstance().reset();

		List<String> requiredPermissions = getProjectsRuntimePermissionList(Build.VERSION.SDK_INT);
		if (requiredPermissions.isEmpty()) {
			stageActivity.stageResourceHolder.initResources();
		} else {
			new RequiresPermissionTask(REQUEST_PERMISSIONS_STAGE_RESOURCE_CREATE, requiredPermissions, R.string.runtime_permission_general) {
				public void task() {
					stageActivity.stageResourceHolder.initResources();
				}
			}.execute(stageActivity);
		}
	}

	static void stagePause(final StageActivity stageActivity) {
		if (checkPermission(stageActivity, getProjectsRuntimePermissionList(Build.VERSION.SDK_INT))) {
			if (stageActivity.nfcAdapter != null) {
				try {
					stageActivity.nfcAdapter.disableForegroundDispatch(stageActivity);
				} catch (IllegalStateException illegalStateException) {
					Log.e(TAG, "Disabling NFC foreground dispatching went wrong!", illegalStateException);
				}
			}

			List<Sprite> sprites = ((StageListener) stageActivity.getApplicationListener()).getSpritesFromStage();

			if (sprites != null) {
				for (Sprite sprite : sprites) {
					sprite.look.pauseParticleEffect();
				}
			}

			get(SpeechRecognitionHolderFactory.class).getInstance().destroy();

			SensorHandler.timerPauseValue = SystemClock.uptimeMillis();

			SensorHandler.stopSensorListeners();
			SoundManager.getInstance().pause();
			MidiSoundManager.getInstance().pause();
			StageActivity.stageListener.menuPause();
			stageActivity.stageAudioFocus.releaseAudioFocus();
			if (stageActivity.cameraManager != null) {
				stageActivity.cameraManager.pause();
			}
			if (stageActivity.vibrationManager != null) {
				stageActivity.vibrationManager.pause();
			}
		}
	}

	public static void stageResume(final StageActivity stageActivity) {
		if (stageActivity.dialogIsShowing()) {
			return;
		}

		if (checkPermission(stageActivity,
				getProjectsRuntimePermissionList(Build.VERSION.SDK_INT))) {
			Brick.ResourcesSet resourcesSet = ProjectManager.getInstance().getCurrentProject().getRequiredResources();
			List<Sprite> spriteList = ProjectManager.getInstance().getCurrentlyPlayingScene().getSpriteList();

			SensorHandler.startSensorListener(stageActivity);

			for (Sprite sprite : spriteList) {
				if (!sprite.getPlaySoundBricks().isEmpty()) {
					stageActivity.stageAudioFocus.requestAudioFocus();
					break;
				}
			}

			List<Sprite> sprites =
					((StageListener) stageActivity.getApplicationListener()).getSpritesFromStage();
			if (sprites != null) {
				for (Sprite sprite : sprites) {
					sprite.look.resumeParticleEffect();
				}
			}

			if (stageActivity.vibrationManager != null) {
				stageActivity.vibrationManager.resume();
			}

			if (stageActivity.cameraManager != null) {
				stageActivity.cameraManager.resume();
			}

			if (resourcesSet.contains(Brick.TEXT_TO_SPEECH)) {
				stageActivity.stageAudioFocus.requestAudioFocus();
			}

			if (resourcesSet.contains(Brick.NFC_ADAPTER)
					&& stageActivity.nfcAdapter != null) {
				stageActivity.nfcAdapter.enableForegroundDispatch(stageActivity, stageActivity.pendingIntent, null, null);
			}

			SoundManager.getInstance().resume();
			MidiSoundManager.getInstance().resume();
			if (stageActivity.stageResourceHolder.initFinished()) {
				StageActivity.stageListener.menuResume();
			}
		}
	}

	static void stageDestroy(StageActivity stageActivity) {
		Project project = ProjectManager.getInstance().getCurrentProject();
		if (checkPermission(stageActivity, getProjectsRuntimePermissionList(Build.VERSION.SDK_INT))) {
			if (stageActivity.brickDialogManager != null) {
				stageActivity.brickDialogManager.dismissAllDialogs();
			}
			stageActivity.vibrationManager = null;
			if (stageActivity.cameraManager != null) {
				stageActivity.cameraManager.destroy();
				stageActivity.cameraManager = null;
			}
			SensorHandler.destroy();
			stageActivity.manageLoadAndFinish();
			StageActivity.stageListener = null;
		}
		resetProjectDrawingState(project);
		ProjectManager.getInstance().setCurrentlyPlayingScene(ProjectManager.getInstance().getCurrentlyEditedScene());
	}

	static void resetProjectDrawingState(Project project) {
		if (project == null) {
			return;
		}
		for (Scene scene : project.getSceneList()) {
			for (Sprite sprite : scene.getSpriteList()) {
				sprite.resetDrawingState();
			}
		}
	}
}
