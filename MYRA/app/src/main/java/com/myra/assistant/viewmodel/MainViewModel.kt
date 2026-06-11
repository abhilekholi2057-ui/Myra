package com.myra.assistant.viewmodel

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.myra.assistant.model.AppCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val commandResult = MutableLiveData<String?>()

    private val appPackageMap = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "settings" to "com.android.settings",
        "calculator" to "com.google.android.calculator",
        "calendar" to "com.google.android.calendar",
        "clock" to "com.google.android.deskclock",
        "phone" to "com.google.android.dialer",
        "contacts" to "com.google.android.contacts",
        "play store" to "com.android.vending",
        "amazon" to "com.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "paytm" to "net.one97.paytm",
        "phonepe" to "com.phonepe.app",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "google pay" to "com.google.android.apps.nbu.paisa.user",
        "zoom" to "us.zoom.videomeetings",
        "meet" to "com.google.android.apps.meetings",
        "teams" to "com.microsoft.teams",
        "discord" to "com.discord",
        "linkedin" to "com.linkedin.android",
        "tiktok" to "com.zhiliaoapp.musically"
    )

    fun executeCommand(command: AppCommand) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            try {
                when (command.type) {
                    "OPEN_APP" -> {
                        val name = command.params["app_name"] ?: return@launch
                        val pkg = findPackage(name)
                        if (pkg != null) {
                            val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
                            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                            commandResult.postValue("$name khol diya")
                        } else {
                            commandResult.postValue("$name nahi mila system mein")
                        }
                    }
                    "CALL" -> {
                        val name = command.params["name"] ?: return@launch
                        val number = resolveContact(name) ?: name
                        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                        commandResult.postValue("Calling $name")
                    }
                    "PRIME_CALL" -> {
                        val index = command.params["index"]?.toIntOrNull() ?: 0
                        val contact = getPrimeContact(index)
                        if (contact != null) {
                            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${contact.second}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                            commandResult.postValue("${contact.first} ko call kar rahi hoon")
                        }
                    }
                    "PRIME_MSG" -> {
                        val index = command.params["index"]?.toIntOrNull() ?: 0
                        val contact = getPrimeContact(index)
                        if (contact != null) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:${contact.second}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(intent)
                        }
                    }
                    "SMS" -> {
                        val name = command.params["name"] ?: return@launch
                        val number = resolveContact(name) ?: name
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$number"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                    }
                    "WHATSAPP_MSG" -> {
                        val name = command.params["name"] ?: return@launch
                        val number = resolveContact(name) ?: ""
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${number.replace("+","")}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                    }
                    "VOLUME_UP" -> {
                        val am = ctx.getSystemService(AudioManager::class.java)
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        commandResult.postValue("Volume badha diya")
                    }
                    "VOLUME_DOWN" -> {
                        val am = ctx.getSystemService(AudioManager::class.java)
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        commandResult.postValue("Volume kam kar diya")
                    }
                    "FLASHLIGHT_ON" -> {
                        val cm = ctx.getSystemService(CameraManager::class.java)
                        val id = cm.cameraIdList.firstOrNull()
                        if (id != null) cm.setTorchMode(id, true)
                        commandResult.postValue("Torch on kar diya")
                    }
                    "FLASHLIGHT_OFF" -> {
                        val cm = ctx.getSystemService(CameraManager::class.java)
                        val id = cm.cameraIdList.firstOrNull()
                        if (id != null) cm.setTorchMode(id, false)
                        commandResult.postValue("Torch off kar diya")
                    }
                    "WIFI_ON", "WIFI_OFF" -> {
                        val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ctx.startActivity(intent)
                    }
                    "BLUETOOTH_ON" -> {
                        BluetoothAdapter.getDefaultAdapter()?.enable()
                        commandResult.postValue("Bluetooth on kar rahi hoon")
                    }
                    "BLUETOOTH_OFF" -> {
                        BluetoothAdapter.getDefaultAdapter()?.disable()
                        commandResult.postValue("Bluetooth off kar diya")
                    }
                }
            } catch (e: Exception) {
                commandResult.postValue("Error: ${e.message}")
            }
        }
    }

    fun acceptCall() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                ctx.getSystemService(TelecomManager::class.java).acceptRingingCall()
            } catch (e: Exception) { }
        }
    }

    fun rejectCall() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                ctx.getSystemService(TelecomManager::class.java).endCall()
            } catch (e: Exception) { }
        }
    }

    private fun findPackage(name: String): String? {
        val lower = name.lowercase()
        appPackageMap.entries.firstOrNull { lower.contains(it.key) }?.let { return it.value }
        val pm = getApplication<Application>().packageManager
        return pm.getInstalledApplications(0).firstOrNull {
            pm.getApplicationLabel(it).toString().lowercase().contains(lower)
        }?.packageName
    }

    fun resolveContact(name: String): String? {
        val ctx = getApplication<Application>()
        val cursor = ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null
        )
        cursor?.use {
            while (it.moveToNext()) {
                val n = it.getString(0) ?: continue
                val num = it.getString(1) ?: continue
                if (n.lowercase().contains(name.lowercase())) return num
            }
        }
        return null
    }

    private fun getPrimeContact(index: Int): Pair<String, String>? {
        val ctx = getApplication<Application>()
        val prefs = ctx.getSharedPreferences("myra_prefs", 0)
        val json = prefs.getString("prime_contacts_json", null)
        if (json != null) {
            val arr = JSONArray(json)
            if (index < arr.length()) {
                val obj = arr.getJSONObject(index)
                return Pair(obj.getString("name"), obj.getString("number"))
            }
        }
        return null
    }
}
