package com.carromaipro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.view.View

class AimOverlayView(context: Context) : View(context) {

    // Main line (Striker to target puck)
    private val mainAimPaint = Paint().apply {
        color = Color.parseColor("#00E676") // Bright Neon Green
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Cushion bounce path (Bank shot reflections)
    private val bounceAimPaint = Paint().apply {
        color = Color.parseColor("#FFD600") // Electric Yellow
        strokeWidth = 5f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(20f, 12f), 0f)
        isAntiAlias = true
    }

    // Target Pocket End Indicator
    private val targetRingPaint = Paint().apply {
        color = Color.parseColor("#FF1744") // Red Ring
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val innerDotPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var activePaths: List<List<PointF>> = emptyList()

    fun updateTrajectories(trajectories: List<List<PointF>>) {
        this.activePaths = trajectories
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (path in activePaths) {
            if (path.size < 2) continue

            // 1. First segment: Striker to impact point
            canvas.drawLine(path[0].x, path[0].y, path[1].x, path[1].y, mainAimPaint)

            // 2. Subsequent segments: Cushion bank bounces
            for (i in 1 until path.size - 1) {
                canvas.drawLine(path[i].x, path[i].y, path[i + 1].x, path[i + 1].y, bounceAimPaint)
            }

            // 3. Pocket indicator
            val dest = path.last()
            canvas.drawCircle(dest.x, dest.y, 26f, targetRingPaint)
            canvas.drawCircle(dest.x, dest.y, 8f, innerDotPaint)
        }
    }
}
