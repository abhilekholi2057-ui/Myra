package com.myra.assistant.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import com.myra.assistant.ai.AudioEngine
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.service.CallMonitorService
import com.myra.assistant.service.MyraOverlayService
import com.myra.assistant.ui.settings.SettingsActivity
import com.myra.assistant.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var orbView: OrbAnimationView
    private lateinit var waveformView: WaveformView
    private lateinit var statusText: TextView
    private lateinit var chatRecycler: RecyclerView
    private lateinit var micButton: ImageButton
    private lateinit var batteryText: TextView
    private lateinit var timeText: TextView

    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    private var geminiLive: GeminiLiveClient? = null
    private var audioEngine: AudioEngine? = null

    private var inputBuffer = StringBuilder()
    private var outputBuffer = StringBuilder()
    private var isMuted = false
    private var isInCallMode = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            isInCallMode = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Auto-save default API key if none exists
        val prefs = getSharedPreferences("myra_prefs", 0)
        if (prefs.getString("api_key", "").isNullOrEmpty()) {
            prefs.edit()
                .putString("api_key", getString(R.string.default_api_key))
                .putString("gemini_model", "models/gemini-2.5-flash-native-audio-preview-12-2025")
                .putString("gemini_voice", "Aoede")
                .putString("personality_mode", "GF")
                .putString("user_name", "Jaan")
                .apply()
        }

        initViews()
        checkPermissions()
        startStatusUpdates()
        registerReceiver(callEndedReceiver, IntentFilter("com.myra.CALL_ENDED"),
            RECEIVER_NOT_EXPORTED)
        Handler(Looper.getMainLooper()).postDelayed({ initGeminiLive() }, 400)
        handleCallIntent(intent)
    }

    private fun initViews() {
        orbView      = findViewById(R.id.orbView)
        waveformView = findViewById(R.id.waveformView)
        statusText   = findViewById(R.id.statusText)
        chatRecycler = findViewById(R.id.chatRecycler)
        micButton    = findViewById(R.id.micButton)
        batteryText  = findViewById(R.id.batteryText)
        timeText     = findViewById(R.id.timeText)

        chatAdapter = ChatAdapter(chatMessages)
        chatRecycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        chatRecycler.adapter = chatAdapter

        micButton.setOnClickListener {
            isMuted = !isMuted
            audioEngine?.setMuted(isMuted)
            micButton.setImageResource(
                if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on
            )
        }
        micButton.setOnLongClickListener {
            audioEngine?.clearQueue()
            geminiLive?.interrupt()
            true
        }

        findViewById<ImageButton>(R.id.settingsBtn).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        viewModel.commandResult.observe(this) { result ->
            if (result != null) {
                geminiLive?.sendText(result)
                viewModel.commandResult.value = null
            }
        }
    }

    private fun initGeminiLive() {
        val prefs = getSharedPreferences("myra_prefs", 0)
        val apiKey = prefs.getString("api_key", getString(R.string.default_api_key)) ?: ""
        val model  = prefs.getString("gemini_model",
            "models/gemini-2.5-flash-native-audio-preview-12-2025") ?: ""
        val voice  = prefs.getString("gemini_voice", "Aoede") ?: "Aoede"
        val personality = prefs.getString("personality_mode", "GF") ?: "GF"
        val userName    = prefs.getString("user_name", "Jaan") ?: "Jaan"
        val systemPrompt = buildSystemPrompt(personality, userName)

        audioEngine = AudioEngine { pcm -> geminiLive?.sendAudio(pcm) }.apply {
            onSpeakingStarted = {
                runOnUiThread {
                    orbView.setState(OrbAnimationView.OrbState.SPEAKING)
                    statusText.text = "Bol rahi hoon..."
                    waveformView.startAnimation()
                }
            }
            onSpeakingStopped = {
                runOnUiThread {
                    orbView.setState(OrbAnimationView.OrbState.LISTENING)
                    statusText.text = "Sun rahi hoon... 👂"
                    waveformView.stopAnimation()
                }
            }
            onAmplitudeChanged = { amp ->
                runOnUiThread { waveformView.setAmplitude(amp) }
            }
        }

        geminiLive = GeminiLiveClient(apiKey, model, voice, systemPrompt).apply {
            onConnected = {
                runOnUiThread {
                    statusText.text = "Connected! Sun rahi hoon..."
                    orbView.setState(OrbAnimationView.OrbState.LISTENING)
                }
                audioEngine?.startRecording()
                audioEngine?.startPlayback()
                Handler(Looper.getMainLooper()).postDelayed({
                    sendText(getGreeting(personality, userName))
                }, 800)
            }
            onDisconnected = {
                runOnUiThread {
                    statusText.text = "Reconnect ho rahi hoon..."
                    orbView.setState(OrbAnimationView.OrbState.IDLE)
                }
            }
            onAudioReceived  = { pcm -> audioEngine?.queueAudio(pcm) }
            onInputTranscript  = { text -> inputBuffer.append(text) }
            onOutputTranscript = { text -> outputBuffer.append(text) }
            onTurnComplete = {
                val userText  = inputBuffer.toString().trim()
                val myraText  = outputBuffer.toString().trim()
                inputBuffer.clear(); outputBuffer.clear()
                runOnUiThread {
                    if (userText.isNotEmpty()) {
                        chatAdapter.addMessage(ChatMessage(userText, true))
                        chatRecycler.scrollToPosition(chatMessages.size - 1)
                        CommandParser.parse(userText)?.let { viewModel.executeCommand(it) }
                    }
                    if (myraText.isNotEmpty()) {
                        chatAdapter.addMessage(ChatMessage(myraText, false))
                        chatRecycler.scrollToPosition(chatMessages.size - 1)
                    }
                }
            }
            onError = { err ->
                runOnUiThread { statusText.text = "Error: ${err.take(60)}" }
            }
        }
        geminiLive?.connect()

        try {
            startForegroundService(Intent(this, MyraOverlayService::class.java))
            startForegroundService(Intent(this, CallMonitorService::class.java))
        } catch (e: Exception) { /* ignore */ }
    }

    private fun buildSystemPrompt(personality: String, userName: String): String {
        val now = SimpleDateFormat("EEEE, dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())
        val base = "Date/time: $now. User: $userName. You are MYRA, AI voice companion. " +
                   "Keep responses short (2-3 sentences), speak naturally.\n"
        return base + when (personality) {
            "GF" -> "Speak Hinglish (Hindi+English mix). Be warm, loving, expressive. " +
                    "Use 'haan', 'acha', 'bilkul'. Say things like 'Haan $userName! Abhi kar deti hoon 💖'"
            "Professional" -> "Formal English only. Precise, 2 sentences max."
            else -> "Friendly Hinglish/English. Balanced helpful tone."
        }
    }

    private fun getGreeting(personality: String, userName: String) = when (personality) {
        "GF"           -> "Hey $userName! Main MYRA hoon, aa gayi hoon. Kya help chahiye? 💖"
        "Professional" -> "Good day $userName. MYRA online, ready to assist."
        else           -> "Hello $userName! Main MYRA hoon. Kaise help karun?"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
    }

    private fun handleCallIntent(intent: Intent) {
        if (intent.getBooleanExtra(CallMonitorService.EXTRA_INCOMING_CALL, false)) {
            val callerName = intent.getStringExtra(CallMonitorService.EXTRA_CALLER_NAME) ?: "Unknown"
            isInCallMode = true
            geminiLive?.sendText("$callerName ka call aa raha hai. Uthau ya reject karu?")
            Handler(Looper.getMainLooper()).postDelayed({ startCallListening() }, 5000)
        }
    }

    private fun startCallListening() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: ""
                when {
                    text.contains("uthao") || text.contains("haan") || text.contains("accept") ->
                        viewModel.acceptCall()
                    text.contains("reject") || text.contains("nahi") || text.contains("mat") ->
                        viewModel.rejectCall()
                }
                isInCallMode = false
            }
            override fun onError(error: Int) { isInCallMode = false }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        speechRecognizer?.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            }
        )
    }

    private fun startStatusUpdates() {
        val handler = Handler(Looper.getMainLooper())
        val r = object : Runnable {
            override fun run() {
                timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val bat = getSystemService(BatteryManager::class.java)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                batteryText.text = "$bat%"
                handler.postDelayed(this, 60_000)
            }
        }
        handler.post(r)
    }

    private fun checkPermissions() {
        val perms = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
        )
        val missing = perms.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    override fun onPause()  { super.onPause();  audioEngine?.setMuted(true) }
    override fun onResume() { super.onResume(); if (!isMuted) audioEngine?.setMuted(false) }

    override fun onDestroy() {
        geminiLive?.disconnect()
        audioEngine?.release()
        speechRecognizer?.destroy()
        unregisterReceiver(callEndedReceiver)
        super.onDestroy()
    }
}
