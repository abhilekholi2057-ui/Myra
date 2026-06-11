package com.myra.assistant.ai

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiLiveClient(
    private val apiKey: String,
    private val model: String,
    private val voiceName: String,
    private val systemPrompt: String
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val WS_URL =
            "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val KEEPALIVE_MS = 8_000L
        private const val RECONNECT_MS = 3_000L
    }

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onAudioReceived: ((ByteArray) -> Unit)? = null
    var onInputTranscript: ((String) -> Unit)? = null
    var onOutputTranscript: ((String) -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(8, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var keepAliveJob: Job? = null
    private var isConnected = false
    private var shouldReconnect = true

    fun connect() {
        shouldReconnect = true
        doConnect()
    }

    private fun doConnect() {
        val request = Request.Builder()
            .url("$WS_URL?key=$apiKey")
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                isConnected = true
                sendSetup(ws)
            }
            override fun onMessage(ws: WebSocket, text: String) {
                parseMessage(text)
            }
            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WS failure: ${t.message}")
                onError?.invoke(t.message ?: "Connection failed")
                handleDisconnect()
            }
        })
    }

    private fun handleDisconnect() {
        isConnected = false
        keepAliveJob?.cancel()
        onDisconnected?.invoke()
        if (shouldReconnect) {
            scope.launch {
                delay(RECONNECT_MS)
                doConnect()
            }
        }
    }

    private fun sendSetup(ws: WebSocket) {
        val msg = JSONObject().put("setup", JSONObject().apply {
            put("model", model)
            put("system_instruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("generation_config", JSONObject().apply {
                put("response_modalities", JSONArray().put("AUDIO"))
                put("speech_config", JSONObject().put("voice_config",
                    JSONObject().put("prebuilt_voice_config",
                        JSONObject().put("voice_name", voiceName))))
                put("temperature", 0.9)
            })
            put("output_audio_transcription", JSONObject())
            put("input_audio_transcription", JSONObject())
        })
        ws.send(msg.toString())
        startKeepAlive()
        onConnected?.invoke()
    }

    private fun parseMessage(text: String) {
        try {
            val sc = JSONObject(text).optJSONObject("serverContent") ?: return
            // Audio
            val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val data = parts.getJSONObject(i)
                        .optJSONObject("inlineData")?.optString("data") ?: continue
                    onAudioReceived?.invoke(Base64.decode(data, Base64.DEFAULT))
                }
            }
            // Transcripts
            sc.optJSONObject("outputTranscription")?.optString("text")
                ?.takeIf { it.isNotEmpty() }?.let { onOutputTranscript?.invoke(it) }
            sc.optJSONObject("inputTranscription")?.optString("text")
                ?.takeIf { it.isNotEmpty() }?.let { onInputTranscript?.invoke(it) }
            // Turn complete
            if (sc.optBoolean("turnComplete")) onTurnComplete?.invoke()
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    fun sendAudio(pcm: ByteArray) {
        if (!isConnected) return
        val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
        webSocket?.send(JSONObject().put("realtime_input", JSONObject().put("media_chunks",
            JSONArray().put(JSONObject()
                .put("mime_type", "audio/pcm;rate=16000")
                .put("data", b64)))).toString())
    }

    fun sendText(text: String) {
        if (!isConnected) return
        webSocket?.send(JSONObject().put("client_content", JSONObject()
            .put("turns", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put("turn_complete", true)).toString())
    }

    fun interrupt() {
        if (!isConnected) return
        webSocket?.send(JSONObject().put("client_content", JSONObject()
            .put("turns", JSONArray())
            .put("turn_complete", true)).toString())
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (isActive && isConnected) {
                delay(KEEPALIVE_MS)
                sendAudio(ByteArray(1024)) // silence
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        isConnected = false
        keepAliveJob?.cancel()
        webSocket?.close(1000, "User disconnect")
        scope.cancel()
    }

    fun isConnected() = isConnected
}
