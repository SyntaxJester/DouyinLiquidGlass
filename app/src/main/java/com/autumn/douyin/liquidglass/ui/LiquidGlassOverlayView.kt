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
 * The floating glass pill — a plain, translucent [View] drawn entirely with
 * Canvas (no screen capture, no shader refraction). See [OverlayController] for
 * the window-bounds background blur that sits behind this.
 *
 * v1.5: the fill is intentionally strong enough to be clearly visible over dark
 * video (previous versions were so faint they looked "empty"). Frostiness is
 * still user-tunable via [ModuleSettings.tintColor]; this base fill is a floor
 * so the bar is never invisible.
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
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    init {
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

        // 1. Base glass fill — a translucent vertical gradient with enough
        //    presence to read over ANY background (bright or dark).
        val fill = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.argb(150, 255, 255, 255), // top: 59% white light-catch
                Color.argb(90, 240, 244, 250),  // upper-mid: 35%
                Color.argb(70, 210, 218, 232),  // mid: ~27% cool
                Color.argb(120, 30, 33, 42)      // bottom: 47% dark for depth
            ),
            floatArrayOf(0f, 0.28f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = fill
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        fillPaint.shader = null

        // 2. Extra user frost tint on top.
        val tintA = (settings.tintColor ushr 24) and 0xFF
        if (tintA > 0) {
            tintPaint.color = settings.tintColor
            canvas.drawRoundRect(rect, radius, radius, tintPaint)
        }

        // 3. Top sheen highlight.
        val hi = (settings.highlightAlpha.coerceIn(0f, 1f) * 255).toInt()
        if (hi > 0) {
            val sheenH = h * 0.55f
            sheenPaint.shader = LinearGradient(
                0f, 0f, 0f, sheenH,
                intArrayOf(Color.argb(hi, 255, 255, 255), Color.argb(0, 255, 255, 255)),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.clipRect(0f, 0f, w, sheenH)
            canvas.drawRoundRect(rect, radius, radius, sheenPaint)
            canvas.restore()
            sheenPaint.shader = null
        }

        // 4. Glass border: bright top rim fading to a faint full outline.
        val sw = dp(1.4f)
        borderPaint.strokeWidth = sw
        val inset = sw / 2f
        val r = RectF(inset, inset, w - inset, h - inset)
        borderPaint.shader = null
        borderPaint.color = Color.argb(48, 255, 255, 255)
        canvas.drawRoundRect(r, radius, radius, borderPaint)
        borderPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.argb(170, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.5f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(r, radius, radius, borderPaint)
        borderPaint.shader = null
    }
}
