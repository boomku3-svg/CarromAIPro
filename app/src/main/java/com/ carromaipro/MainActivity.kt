package com.carromaipro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val OVERLAY_REQ_CODE = 2001
    private val PROJECTION_REQ_CODE = 2002
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var aimSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#4A3B82"))
            setPadding(48, 80, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Header Title
        val title = TextView(this).apply {
            text = "Aiming Carrom"
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val subTitle = TextView(this).apply {
            text = "for Carrom Pool"
            textSize = 14f
            setTextColor(Color.parseColor("#B3B0D6"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
        }

        // Card Container
        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val cardBg = GradientDrawable().apply {
                setColor(Color.parseColor("#7D6CB5"))
                cornerRadius = 28f
            }
            background = cardBg
            setPadding(40, 40, 40, 40)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Switch Row
        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val switchLabel = TextView(this).apply {
            text = "Aiming Carrom"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        aimSwitch = Switch(this).apply {
            isChecked = false
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    verifyPermissionsAndStart()
                } else {
                    stopService(Intent(this@MainActivity, CaptureService::class.java))
                    Toast.makeText(this@MainActivity, "Aim Assist Stopped", Toast.LENGTH_SHORT).show()
                }
            }
        }

        switchRow.addView(switchLabel)
        switchRow.addView(aimSwitch)

        // Force Stop Button
        val btnForceStop = Button(this).apply {
            text = "Force Stop AimCarrom"
            setTextColor(Color.parseColor("#FF5252"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                stopService(Intent(this@MainActivity, CaptureService::class.java))
                aimSwitch.isChecked = false
                Toast.makeText(this@MainActivity, "Service Force Stopped", Toast.LENGTH_SHORT).show()
            }
        }

        cardLayout.addView(switchRow)
        cardLayout.addView(btnForceStop)

        // Spacer
        val spacer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        // Bottom "START GAME" Button
        val btnStartGame = Button(this).apply {
            text = "START GAME"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            val btnBg = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = 24f
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                140
            )
            setOnClickListener {
                launchCarromPool()
            }
        }

        rootLayout.addView(title)
        rootLayout.addView(subTitle)
        rootLayout.addView(cardLayout)
        rootLayout.addView(spacer)
        rootLayout.addView(btnStartGame)

        setContentView(rootLayout)
    }

    private fun verifyPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_REQ_CODE)
        } else {
            startMediaProjection()
        }
    }

    private fun startMediaProjection() {
        startActivityForResult(projectionManager.createScreenCaptureIntent(), PROJECTION_REQ_CODE)
    }

    private fun launchCarromPool() {
        val launchIntent = packageManager.getLaunchIntentForPackage("com.miniclip.carrom")
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "Carrom Pool app not found on this phone!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_REQ_CODE) {
            if (Settings.canDrawOverlays(this)) {
                startMediaProjection()
            } else {
                aimSwitch.isChecked = false
                Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == PROJECTION_REQ_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                val serviceIntent = Intent(this, CaptureService::class.java).apply {
                    putExtra("RESULT_CODE", resultCode)
                    putExtra("DATA_INTENT", data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                Toast.makeText(this, "Aim Assist Running! Open your game.", Toast.LENGTH_SHORT).show()
            } else {
                aimSwitch.isChecked = false
            }
        }
    }
}
