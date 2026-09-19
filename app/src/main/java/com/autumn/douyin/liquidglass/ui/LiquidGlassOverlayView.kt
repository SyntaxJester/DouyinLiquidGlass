package com.autumn.douyin.liquidglass.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import com.autumn.douyin.liquidglass.ModuleLog
import com.autumn.douyin.liquidglass.settings.ModuleSettings

/**
 * The floating glass pill. A plain [View]. It has two rendering modes:
 *
 *  - LIVE  ([liveBackdrop] = true): a fresh snapshot of the pixels behind the
 *    bar is supplied by [DynamicBitmapBackdrop]; we blur + refract it for a
 *    true liquid-glass look. Only safe when the overlay lives in its OWN
 *    window (so PixelCopy of the host window cannot capture us → no feedback).
 *
 *  - STATIC ([liveBackdrop] = false): no backdrop capture at all. We draw a
 *    self-contained frosted-glass pill (gradient + rim highlight). Used as the
 *    fallback when we could not get a dedicated window, which AVOIDS the
 *    white-out feedback loop that happens if PixelCopy captures our own pixels.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@SuppressLint("ViewConstructor")
class LiquidGlassOverlayView(
    context: Context,
    private var settings: ModuleSettings
) : View(context) {

    private val density = resources.displayMetrics.density

    private val refractionShader = RuntimeShader(LiquidGlassShaders.REFRACTION)
    private val highlightShader = RuntimeShader(LiquidGlassShaders.HIGHLIGHT)

    private val contentNode = RenderNode("liquidGlassContent")
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val clipRect = RectF()
    private val clipPath = Path()
    private val srcRect = Rect()
    private val dstRect = Rect()

    /** Whether to consume live backdrop snapshots. Set by OverlayController. */
    @Volatile
    var liveBackdrop: Boolean = true

    /** Latest snapshot of the pixels behind this view (same size as the view). */
    @Volatile
    var backdropBitmap: Bitmap? = null
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    fun applySettings(newSettings: ModuleSettings) {
        settings = newSettings
        invalidate()
    }

    private fun dp(v: Float) = v * density

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val radius = dp(settings.cornerRadiusDp).coerceAtMost(h / 2f)
        clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(clipRect, radius, radius, Path.Direction.CW)

        val bmp = backdropBitmap
        val canDrawLive = liveBackdrop && bmp != null && !bmp.isRecycled && canvas.isHardwareAccelerated

        if (canDrawLive) {
            drawLiveGlass(canvas, bmp!!, w, h, radius)
            // Frost tint over the refracted content.
            tintPaint.color = settings.tintColor
            canvas.drawRoundRect(clipRect, radius, radius, tintPaint)
        } else {
            drawStaticGlass(canvas, w.toFloat(), h.toFloat(), radius)
        }

        drawBorder(w.toFloat(), h.toFloat(), radius, canvas)
        drawHighlight(canvas, w.toFloat(), h.toFloat(), radius)
    }

    /** Self-contained frosted glass — no external pixels, so it can never
     * feed back on itself. Reads as a translucent dark-glass pill. */
    private fun drawStaticGlass(canvas: Canvas, w: Float, h: Float, radius: Float) {
        // Vertical gradient: lighter at top, darker at bottom, all translucent
        // so whatever is behind still shows through as "dark glass".
        val top = Color.argb(64, 255, 255, 255)    // 25% white
        val mid = Color.argb(38, 200, 210, 230)    // ~15% cool white
        val bottom = Color.argb(96, 18, 18, 24)     // ~38% near-black
        val grad = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(top, mid, bottom),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = grad
        canvas.drawRoundRect(clipRect, radius, radius, fillPaint)
        fillPaint.shader = null

        // A little extra frost from settings.tintColor on top.
        tintPaint.color = settings.tintColor
        canvas.drawRoundRect(clipRect, radius, radius, tintPaint)
    }

    private fun drawLiveGlass(canvas: Canvas, bmp: Bitmap, w: Int, h: Int, radius: Float) {
        try {
            refractionShader.setFloatUniform("size", w.toFloat(), h.toFloat())
            refractionShader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
            refractionShader.setFloatUniform("refractionHeight", dp(settings.refractionHeightDp))
            refractionShader.setFloatUniform("refractionAmount", -dp(settings.refractionAmountDp))
            refractionShader.setFloatUniform("depthEffect", if (settings.depthEffect) 1f else 0f)
            refractionShader.setFloatUniform(
                "chromaticAmount",
                if (settings.chromaticAberration) 1f else 0f
            )

            val blurPx = dp(settings.blurRadiusDp).coerceAtLeast(0.01f)
            val blur = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            val refract = RenderEffect.createRuntimeShaderEffect(refractionShader, "content")
            // chain(outer, inner): inner runs first. Blur → then refract.
            contentNode.setRenderEffect(RenderEffect.createChainEffect(refract, blur))

            contentNode.setPosition(0, 0, w, h)
            val rc = contentNode.beginRecording()
            srcRect.set(0, 0, bmp.width, bmp.height)
            dstRect.set(0, 0, w, h)
            rc.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
            contentNode.endRecording()

            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawRenderNode(contentNode)
            canvas.restore()
        } catch (t: Throwable) {
            ModuleLog.w("drawLiveGlass failed, drawing plain blurred bitmap", t)
            canvas.save()
            canvas.clipPath(clipPath)
            dstRect.set(0, 0, w, h)
            canvas.drawBitmap(bmp, null, dstRect, bitmapPaint)
            canvas.restore()
        }
    }

    private fun drawBorder(w: Float, h: Float, radius: Float, canvas: Canvas) {
        borderPaint.strokeWidth = dp(1f)
        borderPaint.color = Color.argb(46, 255, 255, 255) // ~18% white hairline
        val inset = borderPaint.strokeWidth / 2f
        canvas.drawRoundRect(
            inset, inset, w - inset, h - inset,
            radius, radius, borderPaint
        )
    }

    private fun drawHighlight(canvas: Canvas, w: Float, h: Float, radius: Float) {
        try {
            highlightShader.setFloatUniform("size", w, h)
            highlightShader.setFloatUniform("cornerRadii", radius, radius, radius, radius)
            // angle pointing up (-Y): a top rim highlight.
            highlightShader.setFloatUniform("angle", (-Math.PI / 2).toFloat())
            highlightShader.setFloatUniform("falloff", 4f)
            val a = (settings.highlightAlpha.coerceIn(0f, 1f) * 255).toInt()
            highlightShader.setColorUniform("color", Color.argb(a, 255, 255, 255))
            highlightPaint.shader = highlightShader
            // SRC_OVER (default) instead of PLUS so the rim can never accumulate
            // to a white-out even if some self-capture slips through.
            canvas.drawRoundRect(clipRect, radius, radius, highlightPaint)
        } catch (t: Throwable) {
            ModuleLog.w("drawHighlight failed", t)
        }
    }
}
