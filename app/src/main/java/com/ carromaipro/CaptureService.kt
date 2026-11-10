package com.carromaipro

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager

class CaptureService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: AimOverlayView
    private val engine = CarromEngine()
    private val detector = BoardDetector()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(101, buildNotification())
        createAimOverlay()
    }

    private fun createAimOverlay() {
        overlayView = AimOverlayView(this)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // FLAG_NOT_TOUCHABLE allows player taps to pass through directly into Carrom Pool
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(overlayView, params)

        // Generate baseline multi-bounce trajectory preview
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val board = detector.getBoardBounds(width, height)
        val striker = PointF(board.centerX(), board.bottom - (board.height() * 0.18f))
        val sampleCoin = PointF(board.centerX() - (board.width() * 0.22f), board.centerY() - (board.height() * 0.10f))

        val shotPath = engine.calculateBankShots(striker, sampleCoin, board, maxBounces = 2)
        overlayView.updateTrajectories(listOf(shotPath))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "AimAssistChannel",
                "Aim Assist Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "AimAssistChannel")
                .setContentTitle("Aiming Carrom Active")
                .setContentText("Real-time guidelines running over game screen")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Aiming Carrom Active")
                .setContentText("Real-time guidelines running over game screen")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}
