package com.carromaipro

import android.graphics.PointF
import android.graphics.RectF

class BoardDetector {

    /**
     * Resolves the active playable carrom boundary based on screen aspect ratio
     */
    fun getBoardBounds(screenWidth: Float, screenHeight: Float): RectF {
        val sidePadding = screenWidth * 0.04f
        val boardSize = screenWidth - (sidePadding * 2)
        val topOffset = (screenHeight - boardSize) / 2f

        return RectF(sidePadding, topOffset, screenWidth - sidePadding, topOffset + boardSize)
    }

    /**
     * Returns the 4 corner pocket positions
     */
    fun getPockets(board: RectF): List<PointF> {
        val offset = board.width() * 0.05f
        return listOf(
            PointF(board.left + offset, board.top + offset),
            PointF(board.right - offset, board.top + offset),
            PointF(board.left + offset, board.bottom - offset),
            PointF(board.right - offset, board.bottom - offset)
        )
    }
}
