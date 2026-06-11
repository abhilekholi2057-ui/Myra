package com.myra.assistant.ai

import com.myra.assistant.model.AppCommand

object CommandParser {

    fun parse(text: String): AppCommand? {
        val t = text.lowercase().trim()

        // PRIME CONTACTS
        if (t.contains("close friend") || t.contains("best friend") || t.contains("mere yaar")) {
            return if (t.contains("msg") || t.contains("message") || t.contains("sms"))
                AppCommand("PRIME_MSG", mapOf("index" to "0"))
            else
                AppCommand("PRIME_CALL", mapOf("index" to "0"))
        }
        if (t.contains("meri jaan") || t.contains("my love")) {
            return AppCommand("PRIME_MSG", mapOf("index" to "0"))
        }
        if (t.contains("second contact") || t.contains("doosra contact")) {
            return AppCommand("PRIME_CALL", mapOf("index" to "1"))
        }

        // OPEN APP
        val openMatch = Regex("(?:open|kholo|chalo|launch)\\s+(.+)").find(t)
            ?: Regex("(.+?)\\s+(?:kholo|open karo|launch karo)").find(t)
        if (openMatch != null) {
            val appName = openMatch.groupValues[1].trim()
            return AppCommand("OPEN_APP", mapOf("app_name" to appName))
        }

        // CLOSE APP
        val closeMatch = Regex("(?:close|band karo|close karo)\\s+(.+)").find(t)
        if (closeMatch != null) {
            val appName = closeMatch.groupValues[1].trim()
            return AppCommand("CLOSE_APP", mapOf("app_name" to appName))
        }

        // CALL
        val callMatch = Regex("(?:call karo|call kar|call)\\s+(.+?)(?:\\s+ko)?$").find(t)
            ?: Regex("(.+?)\\s+ko call karo").find(t)
        if (callMatch != null) {
            val name = callMatch.groupValues[1].trim()
            if (!name.contains("prime") && !name.contains("friend"))
                return AppCommand("CALL", mapOf("name" to name))
        }

        // SMS
        val smsMatch = Regex("(?:sms|message|msg)\\s+(?:bhejo|send karo|bhej)\\s+(.+?)(?:\\s+ko)?$").find(t)
            ?: Regex("(.+?)\\s+ko\\s+(?:sms|message|msg)").find(t)
        if (smsMatch != null) {
            return AppCommand("SMS", mapOf("name" to smsMatch.groupValues[1].trim()))
        }

        // WHATSAPP
        if (t.contains("whatsapp")) {
            val waMatch = Regex("whatsapp\\s+(?:karo|call karo|msg karo)\\s+(.+?)(?:\\s+ko)?$").find(t)
                ?: Regex("(.+?)\\s+ko\\s+whatsapp").find(t)
            val name = waMatch?.groupValues?.getOrElse(1) { "" }?.trim() ?: ""
            return if (t.contains("call"))
                AppCommand("WHATSAPP_CALL", mapOf("name" to name))
            else
                AppCommand("WHATSAPP_MSG", mapOf("name" to name))
        }

        // VOLUME
        if (t.contains("volume") || t.contains("awaaz")) {
            return if (t.contains("up") || t.contains("badhao") || t.contains("zyada"))
                AppCommand("VOLUME_UP", emptyMap())
            else
                AppCommand("VOLUME_DOWN", emptyMap())
        }

        // TORCH / FLASHLIGHT
        if (t.contains("torch") || t.contains("flashlight") || t.contains("light")) {
            return if (t.contains("on") || t.contains("chalu"))
                AppCommand("FLASHLIGHT_ON", emptyMap())
            else
                AppCommand("FLASHLIGHT_OFF", emptyMap())
        }

        // WIFI
        if (t.contains("wifi") || t.contains("wi-fi")) {
            return if (t.contains("on") || t.contains("chalu"))
                AppCommand("WIFI_ON", emptyMap())
            else
                AppCommand("WIFI_OFF", emptyMap())
        }

        // BLUETOOTH
        if (t.contains("bluetooth")) {
            return if (t.contains("on") || t.contains("chalu"))
                AppCommand("BLUETOOTH_ON", emptyMap())
            else
                AppCommand("BLUETOOTH_OFF", emptyMap())
        }

        return null
    }
}
