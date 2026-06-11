package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.R
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

// ── Data ─────────────────────────────────────────────────────────────────────
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// ── Waveform View ─────────────────────────────────────────────────────────────
class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barCount = 20
    private val barHeights = FloatArray(barCount) { 4f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var isActive = false
    private var amplitude = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 100
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { updateBars(); invalidate() }
    }

    fun startAnimation() { isActive = true; if (!animator.isRunning) animator.start() }
    fun stopAnimation()  { isActive = false }
    fun setAmplitude(amp: Float) { amplitude = amp }

    private fun updateBars() {
        for (i in 0 until barCount) {
            val target = if (isActive) {
                val wave = sin(System.currentTimeMillis() / 200.0 + i * 0.5).toFloat()
                (4f + amplitude * 30f + wave * 10f).coerceAtLeast(4f)
            } else 4f
            barHeights[i] += (target - barHeights[i]) * 0.3f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barW = width.toFloat() / barCount
        for (i in 0 until barCount) {
            val h = barHeights[i].coerceAtMost(height.toFloat())
            paint.color = if (isActive) Color.parseColor("#FF1744") else Color.parseColor("#333333")
            val x = i * barW + barW * 0.2f
            val top = height / 2f - h / 2f
            canvas.drawRoundRect(x, top, x + barW * 0.6f, top + h, 4f, 4f, paint)
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}

// ── Chat Adapter ──────────────────────────────────────────────────────────────
class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_USER = 0
        const val VIEW_MYRA = 1
    }

    fun addMessage(msg: ChatMessage) {
        // Deduplicate consecutive MYRA messages
        if (!msg.isUser && messages.isNotEmpty()
            && !messages.last().isUser
            && messages.last().text == msg.text) return
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    override fun getItemViewType(pos: Int) =
        if (messages[pos].isUser) VIEW_USER else VIEW_MYRA

    override fun getItemCount() = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layout = if (viewType == VIEW_USER) R.layout.item_chat_user else R.layout.item_chat_myra
        val v = LayoutInflater.from(parent.context).inflate(layout, parent, false)
        return object : RecyclerView.ViewHolder(v) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, pos: Int) {
        val msg = messages[pos]
        holder.itemView.findViewById<TextView>(R.id.chatText)?.text = msg.text
        holder.itemView.findViewById<TextView>(R.id.chatTime)?.text =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
    }
}
