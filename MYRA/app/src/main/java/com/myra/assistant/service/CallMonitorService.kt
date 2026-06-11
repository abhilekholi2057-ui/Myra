package com.myra.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.myra.assistant.ui.main.MainActivity

class CallMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "myra_call_monitor"
        const val EXTRA_INCOMING_CALL = "INCOMING_CALL"
        const val EXTRA_CALLER_NAME = "CALLER_NAME"
    }

    private var telephonyManager: TelephonyManager? = null
    private val phoneStateListener = object : PhoneStateListener() {
        @Deprecated("Deprecated in Java")
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    val callerName = if (!phoneNumber.isNullOrEmpty()) resolveCallerName(phoneNumber) else "Unknown"
                    notifyMainActivity(callerName)
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    sendBroadcast(Intent("com.myra.CALL_ENDED"))
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MYRA")
            .setContentText("Monitoring calls...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()
        startForeground(2, notification)
        telephonyManager = getSystemService(TelephonyManager::class.java)
        @Suppress("DEPRECATION")
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun resolveCallerName(number: String): String {
        val stripped = if (number.length > 7) number.takeLast(7) else number
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: continue
                val n = it.getString(1)?.replace(" ", "") ?: continue
                if (n.endsWith(stripped)) return name
            }
        }
        return number
    }

    private fun notifyMainActivity(callerName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_INCOMING_CALL, true)
            putExtra(EXTRA_CALLER_NAME, callerName)
        }
        startActivity(intent)
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "MYRA Call Monitor", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        @Suppress("DEPRECATION")
        telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        super.onDestroy()
    }
}
