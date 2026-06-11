package com.myra.assistant.ui.main

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

class OrbAnimationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class OrbState { IDLE, LISTENING, SPEAKING, THINKING }

    private var state = OrbState.IDLE
    private var amplitude = 0f
    private var rotationAngle = 0f
    private var waveOffset = 0f
    private var pulseScale = 1f
    private var glowAlpha = 150f
    private var thinkingAngle = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Colors
    private val idleColors = intArrayOf(Color.parseColor("#B71C1C"), Color.parseColor("#880E4F"))
    private val listenColors = intArrayOf(Color.parseColor("#FF1744"), Color.parseColor("#D500F9"))
    private val speakColors = intArrayOf(Color.parseColor("#E040FB"), Color.parseColor("#FF1744"))
    private val thinkColors = intArrayOf(Color.parseColor("#40C4FF"), Color.parseColor("#00B0FF"))

    private val rotationAnim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3000; repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { rotationAngle = it.animatedValue as Float; invalidate() }
    }
    private val waveAnim = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 1500; repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { waveOffset = it.animatedValue as Float }
    }
    private val pulseAnim = ValueAnimator.ofFloat(1f, 1.15f, 1f).apply {
        duration = 1500; repeatCount = ValueAnimator.INFINITE
        addUpdateListener { pulseScale = it.animatedValue as Float; invalidate() }
    }
    private val glowAnim = ValueAnimator.ofFloat(120f, 220f, 120f).apply {
        duration = 1500; repeatCount = ValueAnimator.INFINITE
        addUpdateListener { glowAlpha = it.animatedValue as Float }
    }
    private val thinkingAnim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 1000; repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { thinkingAngle = it.animatedValue as Float; invalidate() }
    }

    init {
        pulseAnim.start(); glowAnim.start(); rotationAnim.start(); waveAnim.start()
    }

    fun setState(newState: OrbState) {
        state = newState
        when (newState) {
            OrbState.THINKING -> { thinkingAnim.start() }
            else -> { thinkingAnim.cancel() }
        }
        invalidate()
    }

    fun setAmplitude(amp: Float) {
        amplitude = amp
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) * 0.7f * pulseScale

        val colors = when (state) {
            OrbState.IDLE -> idleColors
            OrbState.LISTENING -> listenColors
            OrbState.SPEAKING -> speakColors
            OrbState.THINKING -> thinkColors
        }

        // 1. Radial glow
        paint.shader = RadialGradient(cx, cy, radius * 1.6f,
            intArrayOf(colors[0] and 0x00FFFFFF or (glowAlpha.toInt() shl 24), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 1.6f, paint)

        // 2. Core orb
        paint.shader = RadialGradient(cx - radius * 0.3f, cy - radius * 0.3f, radius,
            colors, null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null

        // 3. Rotating rings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        for (i in 0..2) {
            paint.color = colors[0] and 0x00FFFFFF or (80 shl 24)
            canvas.save()
            canvas.rotate(rotationAngle + i * 60f, cx, cy)
            canvas.drawCircle(cx, cy, radius + 20f + i * 15f, paint)
            canvas.restore()
        }
        paint.pathEffect = null

        // 4. Wave rings
        if (state == OrbState.LISTENING || state == OrbState.SPEAKING) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = colors[0] and 0x00FFFFFF or (60 shl 24)
            val waveRadius = radius + 40f + amplitude * 30f + sin(waveOffset.toDouble()).toFloat() * 10f
            canvas.drawCircle(cx, cy, waveRadius, paint)
        }

        // 5. Thinking arc
        if (state == OrbState.THINKING) {
            paint.color = thinkColors[0]
            paint.strokeWidth = 4f
            val rect = RectF(cx - radius - 30f, cy - radius - 30f, cx + radius + 30f, cy + radius + 30f)
            canvas.drawArc(rect, thinkingAngle, 90f, false, paint)
            canvas.drawArc(rect, thinkingAngle + 180f, 90f, false, paint)
        }

        // 6. Particles when speaking
        if (state == OrbState.SPEAKING || state == OrbState.LISTENING) {
            paint.style = Paint.Style.FILL
            for (i in 0..11) {
                val angle = Math.toRadians((rotationAngle + i * 30).toDouble())
                val pr = radius + 50f + amplitude * 20f
                val px = cx + (cos(angle) * pr).toFloat()
                val py = cy + (sin(angle) * pr).toFloat()
                paint.color = colors[0] and 0x00FFFFFF or (180 shl 24)
                canvas.drawCircle(px, py, 4f, paint)
            }
        }

        // 7. Inner highlight
        paint.shader = RadialGradient(cx - radius * 0.4f, cy - radius * 0.4f, radius * 0.6f,
            intArrayOf(Color.WHITE and 0x00FFFFFF or (60 shl 24), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        paint.style = Paint.Style.FILL
    }
}
