package com.autumn.douyin.liquidglass.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.annotation.RequiresApi
import com.autumn.douyin.liquidglass.ModuleLog

/**
 * Captures the pixels that sit *behind* the glass pill and feeds them to the
 * overlay as a backdrop bitmap.
 *
 * The default strategy is [PixelCopy], which reads back the window's rendered
 * frame. It works without root but cannot see other apps' surfaces (fine here,
 * we only need Douyin's own content). Capture is throttled to a target FPS so
 * it does not tank scroll performance.
 *
 * If you later add a root/SurfaceControl based capture (see the reference
 * module's CompositeFrameDaemon), implement it as another Strategy and swap it
 * in without touching the overlay.
 *
 * NOTE on self-capture: the overlay is added as its OWN WindowManager window
 * (a sub-window of the activity), NOT as a child of the activity's decor view.
 * That is deliberate — PixelCopy reads back the *activity window's* surface,
 * which does not contain our separate overlay window, so there is no
 * capture-feedback loop. If you ever move the overlay into the decor tree,
 * you must hide it during capture (causing flicker) or switch to a
 * SurfaceControl/root capture that can exclude the overlay layer.
 */
@RequiresApi(Build.VERSION_CODES.O)
class DynamicBitmapBackdrop(
    private val activity: Activity,
    private val overlay: LiquidGlassOverlayView,
    private val targetFps: Int = 30
) {

    private val handler = Handler(Looper.getMainLooper())
    private val frameIntervalMs = (1000L / targetFps).coerceAtLeast(16L)

    @Volatile
    private var running = false

    private var scratch: Bitmap? = null
    private var blownOutStreak = 0

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            captureOnce()
            handler.postDelayed(this, frameIntervalMs)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(captureRunnable)
        ModuleLog.d("DynamicBitmapBackdrop started @${targetFps}fps")
    }

    fun stop() {
        running = false
        handler.removeCallbacks(captureRunnable)
        scratch?.recycle()
        scratch = null
    }

    private fun captureOnce() {
        val window = activity.window ?: return
        val decor = window.decorView
        if (decor.width <= 0 || decor.height <= 0) return

        val w = overlay.width
        val h = overlay.height
        if (w <= 0 || h <= 0) return
        if (!overlay.isAttachedToWindow) return

        // The overlay lives in its OWN sub-window, so we must express its region
        // in the MAIN window's surface coordinates before asking PixelCopy to
        // read that window back. We do this via on-screen positions:
        //   srcInMainWindow = overlayOnScreen - decorOnScreen
        val decorLoc = IntArray(2)
        val ovLoc = IntArray(2)
        decor.getLocationOnScreen(decorLoc)
        overlay.getLocationOnScreen(ovLoc)

        var left = ovLoc[0] - decorLoc[0]
        var top = ovLoc[1] - decorLoc[1]
        // Clamp the source rect inside the decor surface; PixelCopy errors out
        // if the rect leaves the surface bounds.
        left = left.coerceIn(0, (decor.width - w).coerceAtLeast(0))
        top = top.coerceIn(0, (decor.height - h).coerceAtLeast(0))
        val srcRect = Rect(left, top, left + w, top + h)

        val bmp = scratch?.takeIf { it.width == w && it.height == h && !it.isRecycled }
            ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { scratch = it }

        try {
            PixelCopy.request(
                window,
                srcRect,
                bmp,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        if (isBlownOut(bmp)) {
                            // Self-capture feedback detected: the region is
                            // almost pure white. Stop feeding live frames and
                            // let the overlay fall back to static glass so it
                            // can't keep accumulating to white.
                            blownOutStreak++
                            if (blownOutStreak >= 3) {
                                ModuleLog.w("self-capture white-out detected; switching to static glass")
                                overlay.liveBackdrop = false
                                overlay.backdropBitmap = null
                                stop()
                            }
                        } else {
                            blownOutStreak = 0
                            overlay.backdropBitmap = bmp
                        }
                    } else if (ModuleLog.verbose) {
                        ModuleLog.d("PixelCopy result=$result")
                    }
                },
                handler
            )
        } catch (t: Throwable) {
            ModuleLog.w("PixelCopy request failed", t)
        }
    }

    /** Cheap check: sample a grid of pixels; if nearly all are near-white and
     * near-opaque, we're almost certainly looking at our own overlay. */
    private fun isBlownOut(bmp: Bitmap): Boolean {
        if (bmp.isRecycled) return false
        val cols = 6
        val rows = 3
        var white = 0
        var total = 0
        for (yi in 0 until rows) {
            val y = (bmp.height - 1) * yi / (rows - 1).coerceAtLeast(1)
            for (xi in 0 until cols) {
                val x = (bmp.width - 1) * xi / (cols - 1).coerceAtLeast(1)
                val p = bmp.getPixel(x, y)
                val a = (p ushr 24) and 0xFF
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                if (a > 240 && r > 244 && g > 244 && b > 244) white++
                total++
            }
        }
        return total > 0 && white >= (total * 0.9f).toInt()
    }
}
