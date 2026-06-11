package com.myra.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerButtonReceiver : BroadcastReceiver() {
    companion object {
        private var lastScreenOff = 0L
        private const val DOUBLE_PRESS_WINDOW = 600L
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                val now = System.currentTimeMillis()
                if (now - lastScreenOff < DOUBLE_PRESS_WINDOW) {
                    // Double press detected
                    val overlayIntent = Intent(context, MyraOverlayService::class.java)
                    overlayIntent.putExtra("action", "SHOW_OVERLAY")
                    context.startForegroundService(overlayIntent)
                }
                lastScreenOff = now
            }
        }
    }
}
