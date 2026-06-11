package com.myra.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.myra.assistant.ui.main.MainActivity

class MyraOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "myra_overlay_channel"
        var isRunning = false
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MYRA Active")
            .setContentText("Tap to open")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("action")
        if (action == "SHOW_OVERLAY") showOverlay()
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return
        windowManager = getSystemService(WindowManager::class.java)
        val params = WindowManager.LayoutParams(
            (160 * resources.displayMetrics.density).toInt(),
            (160 * resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        overlayView = FrameLayout(this)
        overlayView?.setBackgroundColor(0x99050505.toInt())

        var dX = 0f; var dY = 0f
        overlayView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { dX = params.x - event.rawX; dY = params.y - event.rawY; false }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (event.rawX + dX).toInt()
                    params.y = (event.rawY + dY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val intent = Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    startActivity(intent)
                    false
                }
                else -> false
            }
        }
        windowManager?.addView(overlayView, params)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        super.onDestroy()
    }
}
