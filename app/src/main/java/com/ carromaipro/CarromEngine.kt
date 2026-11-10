package com.carromaipro

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CarromEngine {

    /**
     * Calculates direct line & multi-cushion bank bounces
     */
    fun calculateBankShots(
        origin: PointF,
        targetPuck: PointF,
        board: RectF,
        maxBounces: Int = 2
    ): List<PointF> {
        val points = mutableListOf<PointF>()
        points.add(PointF(origin.x, origin.y))
        points.add(PointF(targetPuck.x, targetPuck.y))

        var currentX = targetPuck.x
        var currentY = targetPuck.y

        var angle = atan2(
            (targetPuck.y - origin.y).toDouble(),
            (targetPuck.x - origin.x).toDouble()
        )

        for (bounce in 0 until maxBounces) {
            var vx = cos(angle).toFloat()
            var vy = sin(angle).toFloat()

            var steps = 0f
            while (steps < 2200f) {
                currentX += vx * 6f
                currentY += vy * 6f
                steps += 6f

                // Left or Right cushion hit
                if (currentX <= board.left || currentX >= board.right) {
                    vx = -vx
                    points.add(PointF(currentX, currentY))
                    break
                }

                // Top or Bottom cushion hit
                if (currentY <= board.top || currentY >= board.bottom) {
                    vy = -vy
                    points.add(PointF(currentX, currentY))
                    break
                }
            }
            angle = atan2(vy.toDouble(), vx.toDouble())
        }

        points.add(PointF(currentX, currentY))
        return points
    }
}
