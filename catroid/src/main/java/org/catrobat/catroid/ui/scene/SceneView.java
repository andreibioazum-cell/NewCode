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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.badlogic.gdx.math.Rectangle;

import org.catrobat.catroid.common.LookData;
import org.catrobat.catroid.content.Look;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.content.Scene;
import org.catrobat.catroid.content.Sprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Engine-style scene preview: draws the current scene (background + sprites)
 * scaled to fit the view, using the same world coordinates as the stage
 * (origin in the center, y axis pointing up, user interface dimension units).
 * Sprites can be selected by tapping and moved by dragging.
 */
public class SceneView extends View {

	public interface OnSpriteClickListener {
		void onSpriteClicked(Sprite sprite);

		void onBackgroundClicked();
	}

	private static final float DRAG_THRESHOLD = 8f;
	private static final int FALLBACK_WORLD_WIDTH = 320;
	private static final int FALLBACK_WORLD_HEIGHT = 480;

	private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private final Paint framePaint = new Paint();
	private final Paint selectionPaint = new Paint();
	private final Paint fillPaint = new Paint();
	private final RectF drawRect = new RectF();

	private Scene scene;
	private Sprite selected;
	private OnSpriteClickListener listener;

	// world extent in user interface dimension units (origin centered)
	private float worldWidth = FALLBACK_WORLD_WIDTH;
	private float worldHeight = FALLBACK_WORLD_HEIGHT;

	private float scale = 1f;

	private boolean dragging = false;
	private boolean moved = false;
	private Sprite dragged;
	private float dragStartWorldX;
	private float dragStartWorldY;
	private float lookStartX;
	private float lookStartY;

	public SceneView(Context context) {
		super(context);
		init();
	}

	public SceneView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		framePaint.setStyle(Paint.Style.STROKE);
		framePaint.setColor(Color.argb(90, 255, 255, 255));
		framePaint.setStrokeWidth(2f);
		selectionPaint.setStyle(Paint.Style.STROKE);
		selectionPaint.setColor(Color.WHITE);
		selectionPaint.setStrokeWidth(2f);
	}

	public void setOnSpriteClickListener(OnSpriteClickListener listener) {
		this.listener = listener;
	}

	public void setScene(Scene scene) {
		this.scene = scene;
		this.selected = null;
		Rectangle rect = worldRectangle(scene);
		worldWidth = rect.width;
		worldHeight = rect.height;
		invalidate();
	}

	public Scene getScene() {
		return scene;
	}

	private static Rectangle worldRectangle(Scene scene) {
		Project project = scene == null ? null : scene.getProject();
		if (project != null) {
			Rectangle rect = project.getScreenRectangle();
			if (rect.width > 0 && rect.height > 0) {
				return rect;
			}
		}
		return new Rectangle(-FALLBACK_WORLD_WIDTH / 2f, -FALLBACK_WORLD_HEIGHT / 2f,
				FALLBACK_WORLD_WIDTH, FALLBACK_WORLD_HEIGHT);
	}

	private void updateTransform() {
		int width = getWidth();
		int height = getHeight();
		if (width <= 0 || height <= 0) {
			return;
		}
		scale = Math.min(width / worldWidth, height / worldHeight);
	}

	// world -> canvas
	private float canvasX(float worldX) {
		return getWidth() / 2f + worldX * scale;
	}

	private float canvasY(float worldY) {
		return getHeight() / 2f - worldY * scale;
	}

	// canvas -> world
	private float toWorldX(float viewX) {
		return (viewX - getWidth() / 2f) / scale;
	}

	private float toWorldY(float viewY) {
		return (getHeight() / 2f - viewY) / scale;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		canvas.drawColor(Color.parseColor("#10131C"));
		if (scene == null) {
			return;
		}
		updateTransform();

		float stageLeft = canvasX(-worldWidth / 2f);
		float stageTop = canvasY(worldHeight / 2f);
		float stageWidth = worldWidth * scale;
		float stageHeight = worldHeight * scale;

		drawBackground(canvas, stageLeft, stageTop, stageWidth, stageHeight);

		List<Sprite> sprites = new ArrayList<>(scene.getSpriteList());
		for (int i = sprites.size() - 1; i >= 0; i--) {
			Sprite sprite = sprites.get(i);
			if (sprite == null || sprite.look == null || scene.getBackgroundSprite() == sprite) {
				continue;
			}
			drawSprite(canvas, sprite);
		}

		canvas.drawRect(stageLeft, stageTop, stageLeft + stageWidth, stageTop + stageHeight, framePaint);
	}

	private void drawBackground(Canvas canvas, float left, float top, float width, float height) {
		Sprite background = scene.getBackgroundSprite();
		Bitmap bitmap = null;
		if (background != null && background.getLookList() != null && !background.getLookList().isEmpty()) {
			LookData lookData = background.getLookList().get(0);
			bitmap = lookData.getThumbnailBitmap();
		}
		if (bitmap == null || bitmap.isRecycled()) {
			fillPaint.setColor(Color.parseColor("#1B2233"));
			canvas.drawRect(left, top, left + width, top + height, fillPaint);
			return;
		}
		float ratio = Math.max(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
		float drawWidth = bitmap.getWidth() * ratio;
		float drawHeight = bitmap.getHeight() * ratio;
		float drawLeft = left + (width - drawWidth) / 2f;
		float drawTop = top + (height - drawHeight) / 2f;
		drawRect.set(drawLeft, drawTop, drawLeft + drawWidth, drawTop + drawHeight);
		canvas.drawBitmap(bitmap, null, drawRect, bitmapPaint);
	}

	private void drawSprite(Canvas canvas, Sprite sprite) {
		Look look = sprite.look;
		Bitmap bitmap = null;
		LookData lookData = look.getLookData();
		if (lookData != null) {
			bitmap = lookData.getThumbnailBitmap();
		}

		float centerX = canvasX(look.getXInUserInterfaceDimensionUnit());
		float centerY = canvasY(look.getYInUserInterfaceDimensionUnit());
		float drawWidth = Math.max(look.getWidthInUserInterfaceDimensionUnit() * scale, 8f);
		float drawHeight = Math.max(look.getHeightInUserInterfaceDimensionUnit() * scale, 8f);

		float rotation = stageRotationDegrees(look);
		if (bitmap != null && !bitmap.isRecycled()) {
			canvas.save();
			canvas.rotate(-rotation, centerX, centerY);
			drawRect.set(centerX - drawWidth / 2f, centerY - drawHeight / 2f,
					centerX + drawWidth / 2f, centerY + drawHeight / 2f);
			canvas.drawBitmap(bitmap, null, drawRect, bitmapPaint);
			canvas.restore();
		} else {
			fillPaint.setColor(Color.parseColor("#8888AA"));
			canvas.drawOval(centerX - drawWidth / 2f, centerY - drawHeight / 2f,
					centerX + drawWidth / 2f, centerY + drawHeight / 2f, fillPaint);
		}

		if (selected == sprite) {
			canvas.drawRect(centerX - drawWidth / 2f - 3f, centerY - drawHeight / 2f - 3f,
					centerX + drawWidth / 2f + 3f, centerY + drawHeight / 2f + 3f, selectionPaint);
		}
	}

	/**
	 * The angle the 3D stage rotates the look with (libgdx, y up, counter clockwise).
	 * Mirrored for the y down canvas in {@link #drawSprite}.
	 */
	private static float stageRotationDegrees(Look look) {
		if (look.getRotationMode() != Look.ROTATION_STYLE_ALL_AROUND) {
			return 0f;
		}
		float direction = look.getMotionDirectionInUserInterfaceDimensionUnit();
		return (Look.DEGREE_UI_OFFSET - direction) % 360f;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (scene == null) {
			return false;
		}
		updateTransform();
		float worldX = toWorldX(event.getX());
		float worldY = toWorldY(event.getY());

		switch (event.getActionMasked()) {
			case MotionEvent.ACTION_DOWN: {
				dragged = findSpriteAt(worldX, worldY);
				dragging = dragged != null;
				moved = false;
				if (dragged != null && dragged.look != null) {
					selected = dragged;
					dragStartWorldX = worldX;
					dragStartWorldY = worldY;
					lookStartX = dragged.look.getXInUserInterfaceDimensionUnit();
					lookStartY = dragged.look.getYInUserInterfaceDimensionUnit();
					invalidate();
				}
				return true;
			}
			case MotionEvent.ACTION_MOVE: {
				if (dragging && dragged != null && dragged.look != null) {
					if (!moved
							&& Math.hypot(worldX - dragStartWorldX, worldY - dragStartWorldY)
							> DRAG_THRESHOLD / scale) {
						moved = true;
					}
					if (moved) {
						float targetX = lookStartX + (worldX - dragStartWorldX);
						float targetY = lookStartY + (worldY - dragStartWorldY);
						dragged.look.setPositionInUserInterfaceDimensionUnit(targetX, targetY);
						invalidate();
					}
				}
				return true;
			}
			case MotionEvent.ACTION_UP: {
				if (dragging && dragged != null && !moved) {
					Sprite clicked = dragged;
					dragging = false;
					if (listener != null) {
						listener.onSpriteClicked(clicked);
					}
				} else if (!dragging) {
					dragging = false;
					if (listener != null) {
						listener.onBackgroundClicked();
					}
				} else {
					dragging = false;
				}
				return true;
			}
			case MotionEvent.ACTION_CANCEL:
				dragging = false;
				return true;
			default:
				return super.onTouchEvent(event);
		}
	}

	private Sprite findSpriteAt(float worldX, float worldY) {
		if (scene == null) {
			return null;
		}
		List<Sprite> sprites = scene.getSpriteList();
		for (int i = sprites.size() - 1; i >= 0; i--) {
			Sprite sprite = sprites.get(i);
			if (sprite == null || sprite.look == null || sprite == scene.getBackgroundSprite()) {
				continue;
			}
			Look look = sprite.look;
			float dx = worldX - look.getXInUserInterfaceDimensionUnit();
			float dy = worldY - look.getYInUserInterfaceDimensionUnit();
			float radius = Math.max(look.getWidthInUserInterfaceDimensionUnit(),
					look.getHeightInUserInterfaceDimensionUnit()) / 2f + 4f;
			if (Math.hypot(dx, dy) <= radius) {
				return sprite;
			}
		}
		return null;
	}
}
