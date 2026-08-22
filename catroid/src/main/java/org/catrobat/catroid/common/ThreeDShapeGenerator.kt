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
package org.catrobat.catroid.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Процедурная отрисовка простых 3D-фигур (изометрическая проекция)
 * для образов спрайтов NewCode.
 */
enum class ThreeDShape { CUBE, SPHERE, PYRAMID, CYLINDER }

object ThreeDShapeGenerator {

    fun generate(shape: ThreeDShape, sizePx: Int, colorInt: Int): Bitmap {
        val size = sizePx.coerceAtLeast(32)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        when (shape) {
            ThreeDShape.CUBE -> drawCube(canvas, size, colorInt)
            ThreeDShape.SPHERE -> drawSphere(canvas, size, colorInt)
            ThreeDShape.PYRAMID -> drawPyramid(canvas, size, colorInt)
            ThreeDShape.CYLINDER -> drawCylinder(canvas, size, colorInt)
        }
        return bitmap
    }

    private fun shade(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).coerceIn(0f, 255f).toInt()
        val g = (Color.green(color) * factor).coerceIn(0f, 255f).toInt()
        val b = (Color.blue(color) * factor).coerceIn(0f, 255f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private fun edgePaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        this.color = shade(color, 0.4f)
    }

    private fun path(s: Float, vararg pts: Pair<Float, Float>): Path = Path().apply {
        moveTo(pts[0].first * s, pts[0].second * s)
        for (i in 1 until pts.size) {
            lineTo(pts[i].first * s, pts[i].second * s)
        }
        close()
    }

    private fun drawCube(canvas: Canvas, size: Int, color: Int) {
        val s = size.toFloat()
        val top = path(s, 0.5f to 0.08f, 0.9f to 0.29f, 0.5f to 0.5f, 0.1f to 0.29f)
        val left = path(s, 0.1f to 0.29f, 0.5f to 0.5f, 0.5f to 0.92f, 0.1f to 0.71f)
        val right = path(s, 0.5f to 0.5f, 0.9f to 0.29f, 0.9f to 0.71f, 0.5f to 0.92f)
        canvas.drawPath(top, fillPaint(shade(color, 1.3f)))
        canvas.drawPath(left, fillPaint(shade(color, 0.85f)))
        canvas.drawPath(right, fillPaint(shade(color, 0.55f)))
        val edge = edgePaint(color)
        canvas.drawPath(top, edge)
        canvas.drawPath(left, edge)
        canvas.drawPath(right, edge)
    }

    private fun drawSphere(canvas: Canvas, size: Int, color: Int) {
        val c = size / 2f
        val r = size * 0.42f
        val shader = RadialGradient(
            c - r * 0.35f, c - r * 0.4f, r * 1.2f,
            intArrayOf(shade(color, 1.6f), color, shade(color, 0.4f)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawCircle(c, c, r, paint)
        val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(160, 255, 255, 255)
        }
        canvas.drawOval(
            RectF(c - r * 0.4f, c - r * 0.55f, c - r * 0.08f, c - r * 0.3f),
            highlight
        )
        canvas.drawCircle(c, c, r, edgePaint(color))
    }

    private fun drawPyramid(canvas: Canvas, size: Int, color: Int) {
        val s = size.toFloat()
        val apex = 0.5f to 0.06f
        val back = 0.5f to 0.52f
        val left = 0.08f to 0.7f
        val front = 0.5f to 0.94f
        val right = 0.92f to 0.7f
        val base = path(s, left, back, right, front)
        canvas.drawPath(base, fillPaint(shade(color, 0.45f)))
        val faceLeft = path(s, apex, left, front)
        val faceRight = path(s, apex, front, right)
        val faceBack = path(s, apex, back, left)
        canvas.drawPath(faceBack, fillPaint(shade(color, 0.7f)))
        canvas.drawPath(faceLeft, fillPaint(shade(color, 0.9f)))
        canvas.drawPath(faceRight, fillPaint(shade(color, 0.6f)))
        val edge = edgePaint(color)
        canvas.drawPath(base, edge)
        canvas.drawPath(faceLeft, edge)
        canvas.drawPath(faceRight, edge)
    }

    private fun drawCylinder(canvas: Canvas, size: Int, color: Int) {
        val s = size.toFloat()
        val left = s * 0.2f
        val right = s * 0.8f
        val topY = s * 0.24f
        val bottomY = s * 0.9f
        val half = s * 0.07f

        val bottomEllipse = RectF(left, bottomY - half, right, bottomY + half)
        canvas.drawOval(bottomEllipse, fillPaint(shade(color, 0.45f)))

        val sideShader = LinearGradient(
            left, 0f, right, 0f,
            shade(color, 0.5f), shade(color, 1.15f),
            Shader.TileMode.CLAMP
        )
        val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = sideShader }
        canvas.drawRect(left, topY, right, bottomY, sidePaint)

        val topEllipse = RectF(left, topY - half, right, topY + half)
        canvas.drawOval(topEllipse, fillPaint(shade(color, 1.35f)))

        val edge = edgePaint(color)
        canvas.drawLine(left, topY, left, bottomY, edge)
        canvas.drawLine(right, topY, right, bottomY, edge)
        canvas.drawOval(topEllipse, edge)
        canvas.drawOval(bottomEllipse, edge)
    }
}
