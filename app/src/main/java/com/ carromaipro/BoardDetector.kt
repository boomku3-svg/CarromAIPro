package com.carromaipro

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

data class DetectionResult(
    val bounds: BoardRect,
    val discs: List<Disc>,
    val confidence: Double,
    val message: String
)

/** Lightweight offline image analysis. It estimates the board rectangle and circular high-contrast
 * components. Always review/edit detections: screenshots vary by game, theme and compression. */
object BoardDetector {
    fun detect(bitmap: Bitmap): DetectionResult {
        val w = bitmap.width
        val h = bitmap.height
        val step = max(2, min(w, h) / 360)
        val xScores = DoubleArray(w / step + 1)
        val yScores = DoubleArray(h / step + 1)
        var samples = 0
        for (y in step until h - step step step) {
            for (x in step until w - step step step) {
                val c = bitmap.getPixel(x, y)
                val r = Color.red(c).toDouble()
                val g = Color.green(c).toDouble()
                val b = Color.blue(c).toDouble()
                val sat = max(r, max(g, b)) - min(r, min(g, b))
                val lum = (r + g + b) / 3.0
                val edge = abs(lum - (Color.red(bitmap.getPixel(x-step,y)) +
                    Color.green(bitmap.getPixel(x-step,y)) + Color.blue(bitmap.getPixel(x-step,y))) / 3.0) +
                    abs(lum - (Color.red(bitmap.getPixel(x,y-step)) +
                    Color.green(bitmap.getPixel(x,y-step)) + Color.blue(bitmap.getPixel(x,y-step))) / 3.0)
                if (sat < 95 && lum in 65.0..235.0) {
                    xScores[x / step] += edge
                    yScores[y / step] += edge
                }
                samples++
            }
        }
        // Broad safe bounds; strong edge peaks can refine these estimates.
        val fallback = BoardRect(w * 0.06, h * 0.08, w * 0.94, h * 0.92)
        val bx = peakBounds(xScores, step, w, fallback.left, fallback.right)
        val by = peakBounds(yScores, step, h, fallback.top, fallback.bottom)
        val bounds = BoardRect(bx.first, by.first, bx.second, by.second)
        val discs = detectBlobs(bitmap, bounds)
        val confidence = if (discs.isEmpty()) 0.35 else (0.45 + discs.size * 0.035).coerceAtMost(0.82)
        return DetectionResult(bounds, discs, confidence,
            "Estimated board and ${discs.size} possible discs. Confirm positions before aiming.")
    }

    private fun peakBounds(scores: DoubleArray, step: Int, limit: Int, fallbackA: Double, fallbackB: Double): Pair<Double, Double> {
        if (scores.size < 10) return fallbackA to fallbackB
        val smooth = scores.indices.map { i ->
            (max(0, i-2)..min(scores.lastIndex, i+2)).sumOf { scores[it] } / 5.0
        }
        val peaks = smooth.indices.sortedByDescending { smooth[it] }.take(8).sorted()
        val a = peaks.firstOrNull()?.times(step)?.toDouble() ?: fallbackA
        val b = peaks.lastOrNull()?.times(step)?.toDouble() ?: fallbackB
        if (b - a < limit * 0.42 || a < limit * 0.02 || b > limit * 0.98) return fallbackA to fallbackB
        return max(fallbackA, a) to min(fallbackB, b)
    }

    private fun detectBlobs(bitmap: Bitmap, board: BoardRect): List<Disc> {
        val w = bitmap.width; val h = bitmap.height
        val scale = max(1, min(w, h) / 420)
        val gw = (w + scale - 1) / scale
        val gh = (h + scale - 1) / scale
        val seen = BooleanArray(gw * gh)
        val result = mutableListOf<Disc>()
        val minX = (board.left / scale).toInt().coerceIn(0, gw-1)
        val maxX = (board.right / scale).toInt().coerceIn(0, gw-1)
        val minY = (board.top / scale).toInt().coerceIn(0, gh-1)
        val maxY = (board.bottom / scale).toInt().coerceIn(0, gh-1)
        fun isDisc(x: Int, y: Int): Boolean {
            val c = bitmap.getPixel((x*scale).coerceAtMost(w-1), (y*scale).coerceAtMost(h-1))
            val r=Color.red(c); val g=Color.green(c); val b=Color.blue(c)
            val mx=max(r,max(g,b)); val mn=min(r,min(g,b))
            return (mx-mn < 65 && (mx < 125 || mn > 175)) || (mx-mn > 95 && r > g*1.18)
        }
        for (y in minY..maxY step 2) for (x in minX..maxX step 2) {
            val idx=y*gw+x
            if (seen[idx] || !isDisc(x,y)) continue
            val q=ArrayDeque<Pair<Int,Int>>(); q.add(x to y); seen[idx]=true
            var sx=0.0; var sy=0.0; var count=0; var minbx=x; var maxbx=x; var minby=y; var maxby=y
            while(q.isNotEmpty() && count < 2500) {
                val (cx,cy)=q.removeFirst(); count++; sx+=cx; sy+=cy
                minbx=min(minbx,cx); maxbx=max(maxbx,cx); minby=min(minby,cy); maxby=max(maxby,cy)
                for ((nx,ny) in listOf(cx-1 to cy,cx+1 to cy,cx to cy-1,cx to cy+1)) {
                    if(nx !in minX..maxX || ny !in minY..maxY) continue
                    val ni=ny*gw+nx
                    if(!seen[ni] && isDisc(nx,ny)){seen[ni]=true;q.add(nx to ny)}
                }
            }
            val bw=maxbx-minbx+1; val bh=maxby-minby+1
            val ratio=bw.toDouble()/bh
            if(count >= 7 && count < 1800 && ratio in 0.55..1.8 && bw in 3..40 && bh in 3..40) {
                val cx=sx/count*scale; val cy=sy/count*scale
                val radius=(bw+bh)*scale/4.0
                if(result.none { it.center.distance(Vec2(cx,cy)) < radius*1.4 }) {
                    result.add(Disc("D${result.size+1}",Vec2(cx,cy),radius,Disc.Kind.UNKNOWN))
                }
            }
        }
        return result.take(30)
    }
}
