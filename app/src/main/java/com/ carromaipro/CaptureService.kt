package com.carromaipro

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager

class CaptureService : Service() {
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private var thread:HandlerThread?=null
    companion object { @Volatile var onFrame:((Bitmap)->Unit)?=null; private const val CHANNEL="carrom_capture" }
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        createChannel()
        val notification=if(Build.VERSION.SDK_INT>=26) Notification.Builder(this,CHANNEL)
            .setContentTitle("Carrom AI Pro screen capture").setContentText("Waiting for a game screenshot")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build()
        else Notification.Builder(this).setContentTitle("Carrom AI Pro capture").setSmallIcon(android.R.drawable.ic_menu_camera).build()
        startForeground(41,notification)
        val code=intent?.getIntExtra("code",0) ?: 0
        val data=if(Build.VERSION.SDK_INT>=33) intent?.getParcelableExtra("data",Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra("data")
        if(data!=null) startCapture(code,data)
        return START_NOT_STICKY
    }
    private fun startCapture(code:Int,data:Intent) {
        try {
            val mgr=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection=mgr.getMediaProjection(code,data)
            val metrics=DisplayMetrics()
            @Suppress("DEPRECATION") (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val width=metrics.widthPixels; val height=metrics.heightPixels; val density=metrics.densityDpi
            reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2)
            thread=HandlerThread("CarromFrameCapture").apply{start()}
            reader?.setOnImageAvailableListener({ r ->
                val image=r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val plane=image.planes[0]; val buffer=plane.buffer
                    val pixelStride=plane.pixelStride; val rowStride=plane.rowStride
                    val rowPadding=rowStride-pixelStride*width
                    val padded=Bitmap.createBitmap(width+rowPadding/pixelStride,height,Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(buffer)
                    val frame=Bitmap.createBitmap(padded,0,0,width,height)
                    padded.recycle(); onFrame?.invoke(frame)
                    stopSelf()
                } catch(_:Exception) { } finally { image.close() }
            },android.os.Handler(thread!!.looper))
            display=projection?.createVirtualDisplay("CarromAIPro",width,height,density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,null)
        } catch(e:Exception){stopSelf()}
    }
    private fun createChannel() {
        if(Build.VERSION.SDK_INT>=26) {
            val nm=getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL,"Screen capture",NotificationManager.IMPORTANCE_LOW))
        }
    }
    override fun onDestroy() {
        display?.release();reader?.close();projection?.stop();thread?.quitSafely()
        super.onDestroy()
    }
}
