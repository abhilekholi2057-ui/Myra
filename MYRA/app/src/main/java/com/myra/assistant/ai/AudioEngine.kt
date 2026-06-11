package com.myra.assistant.ai

import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.sqrt

class AudioEngine(private val onSend: (ByteArray) -> Unit) {

    companion object {
        private const val TAG = "AudioEngine"
        private const val MIC_SAMPLE_RATE = 16000
        private const val SPEAKER_SAMPLE_RATE = 24000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE = 1024
    }

    var onSpeakingStarted: (() -> Unit)? = null
    var onSpeakingStopped: (() -> Unit)? = null
    var onAmplitudeChanged: ((Float) -> Unit)? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isRecording = false
    private var isPlaying = false
    private var isMuted = false
    var isSpeaking = false
        private set

    fun startRecording() {
        val minBuf = AudioRecord.getMinBufferSize(MIC_SAMPLE_RATE, CHANNEL_IN, ENCODING)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MIC_SAMPLE_RATE, CHANNEL_IN, ENCODING,
            maxOf(minBuf, CHUNK_SIZE * 4)
        )
        audioRecord?.startRecording()
        isRecording = true
        scope.launch { recordLoop() }
    }

    fun startPlayback() {
        val minBuf = AudioTrack.getMinBufferSize(SPEAKER_SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SPEAKER_SAMPLE_RATE)
                    .setChannelMask(CHANNEL_OUT)
                    .setEncoding(ENCODING)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, CHUNK_SIZE * 8))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
        isPlaying = true
        scope.launch { playbackLoop() }
    }

    private suspend fun recordLoop() {
        val buffer = ByteArray(CHUNK_SIZE)
        while (isRecording) {
            val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
            if (read > 0 && !isMuted && !isSpeaking) {
                val chunk = buffer.copyOf(read)
                val rms = calculateRms(chunk)
                onAmplitudeChanged?.invoke(rms)
                onSend(chunk)
            }
        }
    }

    private suspend fun playbackLoop() {
        while (isPlaying) {
            val chunk = withContext(Dispatchers.IO) {
                audioQueue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            if (chunk != null) {
                if (!isSpeaking) {
                    isSpeaking = true
                    withContext(Dispatchers.Main) { onSpeakingStarted?.invoke() }
                }
                audioTrack?.write(chunk, 0, chunk.size)
            } else {
                if (isSpeaking && audioQueue.isEmpty()) {
                    isSpeaking = false
                    withContext(Dispatchers.Main) { onSpeakingStopped?.invoke() }
                }
            }
        }
    }

    fun queueAudio(pcm: ByteArray) {
        audioQueue.offer(pcm)
    }

    fun clearQueue() {
        audioQueue.clear()
        audioTrack?.flush()
        if (isSpeaking) {
            isSpeaking = false
            scope.launch(Dispatchers.Main) { onSpeakingStopped?.invoke() }
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    private fun calculateRms(pcm: ByteArray): Float {
        var sum = 0.0
        for (i in 0 until pcm.size - 1 step 2) {
            val sample = (pcm[i + 1].toInt() shl 8 or (pcm[i].toInt() and 0xFF)).toShort()
            sum += sample * sample
        }
        val rms = sqrt(sum / (pcm.size / 2))
        return (rms / 32768f).coerceIn(0f, 1f)
    }

    fun release() {
        isRecording = false
        isPlaying = false
        audioRecord?.stop()
        audioRecord?.release()
        audioTrack?.stop()
        audioTrack?.release()
        scope.cancel()
    }
}
