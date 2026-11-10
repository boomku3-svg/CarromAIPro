package com.carromaipro

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class AimOverlayView(context: Context) : View(context) {
    var bitmap: Bitmap? = null
    var board: BoardRect? = null
    var discs: List<Disc> = emptyList()
    var selectedShot: Shot? = null
    var onBoardTap: ((Vec2) -> Unit)? = null
    private val imagePaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.YELLOW; strokeWidth=4f; style=Paint.Style.STROKE }
    private val boardPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.GREEN; strokeWidth=4f; style=Paint.Style.STROKE }
    private val discPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.CYAN; strokeWidth=3f; style=Paint.Style.STROKE }
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE; textSize=28f; style=Paint.Style.FILL; setShadowLayer(4f,1f,1f,Color.BLACK) }
    private var scaleFactor=1f; private var dx=0f; private var dy=0f
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp=bitmap ?: return
        val s=min(width.toFloat()/bmp.width,height.toFloat()/bmp.height)
        scaleFactor=s; dx=(width-bmp.width*s)/2; dy=(height-bmp.height*s)/2
        canvas.save(); canvas.translate(dx,dy); canvas.scale(s,s)
        canvas.drawBitmap(bmp,0f,0f,imagePaint)
        board?.let { canvas.drawRect(it.left.toFloat(),it.top.toFloat(),it.right.toFloat(),it.bottom.toFloat(),boardPaint) }
        discs.forEach { d ->
            canvas.drawCircle(d.center.x.toFloat(),d.center.y.toFloat(),d.radius.toFloat(),discPaint)
            canvas.drawText(d.id,d.center.x.toFloat()+d.radius.toFloat(),d.center.y.toFloat(),textPaint)
        }
        selectedShot?.let { shot ->
            val p=Paint(linePaint).apply { color=Color.YELLOW }
            for(i in 0 until shot.strikerPath.lastIndex) canvas.drawLine(shot.strikerPath[i].x.toFloat(),shot.strikerPath[i].y.toFloat(),shot.strikerPath[i+1].x.toFloat(),shot.strikerPath[i+1].y.toFloat(),p)
            p.color=Color.MAGENTA
            for(i in 0 until shot.coinPath.lastIndex) canvas.drawLine(shot.coinPath[i].x.toFloat(),shot.coinPath[i].y.toFloat(),shot.coinPath[i+1].x.toFloat(),shot.coinPath[i+1].y.toFloat(),p)
        }
        canvas.restore()
    }
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if(event.action==android.view.MotionEvent.ACTION_UP && scaleFactor>0) {
            onBoardTap?.invoke(Vec2((event.x-dx)/scaleFactor.toDouble(),(event.y-dy)/scaleFactor.toDouble()))
            return true
        }
        return true
    }
}
