package com.autumn.douyin.liquidglass.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
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
 * The floating glass pill. A plain [View] added to the target activity's window
 * via WindowManager. Each frame it:
 *   1. Draws the latest backdrop snapshot into a hardware [RenderNode],
 *   2. Chains a Gaussian blur → refraction [RuntimeShader] as a [RenderEffect],
 *   3. Draws the node clipped to a rounded rect,
 *   4. Paints a frost tint + rim highlight on top.
 *
 * The backdrop bitmap is supplied externally by [DynamicBitmapBackdrop] so the
 * capture strategy (PixelCopy / SurfaceControl / root daemon) can change
 * without touching rendering.
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
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipRect = RectF()
    private val clipPath = Path()
    private val srcRect = Rect()
    private val dstRect = Rect()

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
        if (bmp != null && !bmp.isRecycled && canvas.isHardwareAccelerated) {
            drawGlass(canvas, bmp, w, h, radius)
        } else {
            // No backdrop yet (or software canvas): frosted placeholder.
            tintPaint.color = 0x40000000
            canvas.drawRoundRect(clipRect, radius, radius, tintPaint)
        }

        // Frost tint over the refracted content.
        tintPaint.color = settings.tintColor
        canvas.drawRoundRect(clipRect, radius, radius, tintPaint)

        // Top rim highlight.
        drawHighlight(canvas, w.toFloat(), h.toFloat(), radius)
    }

    private fun drawGlass(canvas: Canvas, bmp: Bitmap, w: Int, h: Int, radius: Float) {
        try {
            // Configure the refraction shader uniforms. The "content" input is
            // supplied by the RenderEffect from the node's recorded pixels, so
            // we must NOT call setInputShader here.
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
            ModuleLog.w("drawGlass failed, drawing plain blurred bitmap", t)
            canvas.save()
            canvas.clipPath(clipPath)
            dstRect.set(0, 0, w, h)
            canvas.drawBitmap(bmp, null, dstRect, bitmapPaint)
            canvas.restore()
        }
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
            highlightPaint.blendMode = BlendMode.PLUS
            canvas.drawRoundRect(clipRect, radius, radius, highlightPaint)
        } catch (t: Throwable) {
            ModuleLog.w("drawHighlight failed", t)
        }
    }
}
