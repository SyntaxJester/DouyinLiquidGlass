package com.autumn.douyin.liquidglass.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import com.autumn.douyin.liquidglass.settings.ModuleSettings

/**
 * The floating glass pill — a plain, always-translucent [View].
 *
 * Design decision (v1.3): NO screen capture, NO RuntimeShader refraction in the
 * default path. Those caused a self-capture feedback loop that white-outed the
 * bar on some ROMs. Instead the frosted look comes from two robust pieces:
 *
 *   1. Real background blur behind the window, set by [OverlayController] via
 *      WindowManager.LayoutParams.setBlurBehindRadius (API 31+). This is a
 *      system-level blur of whatever is *behind* the window — it never captures
 *      our own pixels, so it can never accumulate to white.
 *   2. This view draws only translucent Canvas gradients (tint + edge rims +
 *      top highlight). Every layer has alpha well below opaque, so the video
 *      always shows through. There is no code path that can produce solid white.
 */
@RequiresApi(Build.VERSION_CODES.S)
@SuppressLint("ViewConstructor")
class LiquidGlassOverlayView(
    context: Context,
    private var settings: ModuleSettings
) : View(context) {

    private val density = resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    init {
        // Draw our own content; never let the framework paint an opaque bg.
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun applySettings(newSettings: ModuleSettings) {
        settings = newSettings
        invalidate()
    }

    private fun dp(v: Float) = v * density

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = dp(settings.cornerRadiusDp).coerceAtMost(h / 2f)
        rect.set(0f, 0f, w, h)

        // 1. Base glass fill: a vertical gradient that is BRIGHT & translucent
        //    at the very top (the light-catching rim), nearly CLEAR through the
        //    middle (so the blurred video reads through), and slightly DARK at
        //    the bottom for depth. No stop is anywhere near opaque.
        val fill = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.argb(110, 255, 255, 255), // top rim: 43% white
                Color.argb(38, 255, 255, 255),  // upper: 15% white
                Color.argb(28, 210, 220, 235),  // middle: ~11% cool white (clear)
                Color.argb(64, 12, 14, 20)       // bottom: 25% near-black
            ),
            floatArrayOf(0f, 0.18f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = fill
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        fillPaint.shader = null

        // 2. User frost tint (alpha from settings.tintColor).
        tintPaint.color = settings.tintColor
        canvas.drawRoundRect(rect, radius, radius, tintPaint)

        // 3. Bright hairline along the TOP edge, faint dark along the bottom —
        //    the classic glass border.
        drawEdgeRims(canvas, w, h, radius)

        // 4. Soft top highlight sheen.
        val hi = (settings.highlightAlpha.coerceIn(0f, 1f) * 255).toInt()
        if (hi > 0) {
            val sheenH = h * 0.5f
            val sheen = LinearGradient(
                0f, 0f, 0f, sheenH,
                intArrayOf(Color.argb(hi, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                null,
                Shader.TileMode.CLAMP
            )
            topHighlightPaint.shader = sheen
            canvas.save()
            canvas.clipRect(0f, 0f, w, sheenH)
            canvas.drawRoundRect(rect, radius, radius, topHighlightPaint)
            canvas.restore()
            topHighlightPaint.shader = null
        }
    }

    private fun drawEdgeRims(canvas: Canvas, w: Float, h: Float, radius: Float) {
        val sw = dp(1.2f)
        borderPaint.strokeWidth = sw
        val inset = sw / 2f
        val r = RectF(inset, inset, w - inset, h - inset)
        // Full subtle border.
        borderPaint.shader = null
        borderPaint.color = Color.argb(38, 255, 255, 255)
        canvas.drawRoundRect(r, radius, radius, borderPaint)
        // Brighter top-edge accent via a gradient stroke.
        borderPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.argb(140, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.5f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(r, radius, radius, borderPaint)
        borderPaint.shader = null
    }
}
