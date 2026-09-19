package com.autumn.douyin.liquidglass.ui

import android.app.Activity
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.autumn.douyin.liquidglass.ModuleLog
import com.autumn.douyin.liquidglass.nativebar.NativeBottomBarLocator
import com.autumn.douyin.liquidglass.settings.ModuleSettings

/**
 * Owns one glass overlay for one [Activity].
 *
 * v1.4 rendering model: NO screen capture, NO screen-wide blur. The frosted
 * look is produced by
 *   (a) WINDOW-BOUNDS background blur (mBackgroundBlurRadius, API 31+): blurs
 *       only the content within the pill's own rounded bounds, never the whole
 *       screen — so the video above the bar stays sharp, and
 *   (b) translucent Canvas gradients drawn by [LiquidGlassOverlayView].
 *
 * History: v1.0-1.2 used PixelCopy → white-out (Douyin video is a hardware
 * SurfaceView PixelCopy can't read). v1.3 used FLAG_BLUR_BEHIND → blurred the
 * ENTIRE screen behind the window (the whole video went fuzzy). Both wrong;
 * this version blurs only inside the bar.
 *
 * Placement (see [ModuleSettings.manualPlacement]):
 *   - manual : pin to the bottom of the screen. Version-proof.
 *   - auto   : align with Douyin's native bar via [NativeBottomBarLocator].
 */
@RequiresApi(Build.VERSION_CODES.S)
class OverlayController(
    private val activity: Activity,
    private var settings: ModuleSettings
) {

    private val density = activity.resources.displayMetrics.density
    private var overlay: LiquidGlassOverlayView? = null
    private var attached = false
    private var dedicatedWindow = false
    private var toastShown = false

    private fun dp(v: Float) = (v * density).toInt()

    fun install() {
        if (attached) return
        val view = LiquidGlassOverlayView(activity, settings)
        overlay = view

        if (!tryAttach(view)) {
            ModuleLog.e("all window attach strategies failed for ${activity.javaClass.name}")
            overlay = null
            return
        }
        attached = true

        view.post {
            reposition()
            maybeToast()
        }
    }

    private fun tryAttach(view: LiquidGlassOverlayView): Boolean {
        val token = activity.window?.decorView?.windowToken

        val windowTypes = intArrayOf(
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.TYPE_APPLICATION
        )
        for (type in windowTypes) {
            try {
                val params = buildLayoutParams(type)
                if (type == WindowManager.LayoutParams.TYPE_APPLICATION_PANEL) {
                    params.token = token
                }
                applyBackgroundBlur(params)
                activity.windowManager.addView(view, params)
                dedicatedWindow = true
                ModuleLog.i("overlay attached (windowType=$type) to ${activity.javaClass.simpleName}")
                return true
            } catch (t: Throwable) {
                ModuleLog.w("attach windowType=$type failed: ${t.message}")
            }
        }

        // Fallback: content child. No cross-window blur available here, but the
        // overlay is still fully translucent, so it just looks like a lighter
        // frosted pill (never white).
        try {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
                ?: (activity.window?.decorView as? ViewGroup)
            if (content != null) {
                val lp = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(settings.barHeightDp)
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                }
                content.addView(view, lp)
                dedicatedWindow = false
                ModuleLog.i("overlay attached as content child to ${activity.javaClass.simpleName}")
                return true
            }
        } catch (t: Throwable) {
            ModuleLog.w("attach as content child failed: ${t.message}")
        }
        return false
    }

    /**
     * BACKGROUND blur — blurs ONLY the content within this window's own bounds
     * (the pill), never the whole screen.
     *
     * The public counterpart is [android.view.Window.setBackgroundBlurRadius];
     * for a WindowManager-added view we set the same value on the hidden
     * LayoutParams field via reflection. If that's unavailable we silently fall
     * back to translucent-only glass (still looks fine, just not blurred).
     *
     * NOTE: this deliberately does NOT use FLAG_BLUR_BEHIND / blurBehindRadius —
     * that blurs the entire screen behind the window (the v1.3.0 bug).
     */
    private fun applyBackgroundBlur(params: WindowManager.LayoutParams) {
        val radiusPx = dp(settings.blurRadiusDp).coerceIn(0, 80)
        if (radiusPx <= 0) return
        try {
            if (!activity.windowManager.isCrossWindowBlurEnabled) {
                ModuleLog.d("cross-window blur unsupported/disabled; translucent-only glass")
                return
            }
            val field = WindowManager.LayoutParams::class.java
                .getDeclaredField("mBackgroundBlurRadius")
            field.isAccessible = true
            field.setInt(params, radiusPx)
            ModuleLog.d("background blur radius=${radiusPx}px (window-bounds only)")
        } catch (t: Throwable) {
            ModuleLog.w("background blur unavailable (${t.message}); translucent-only glass")
        }
    }

    private fun maybeToast() {
        if (toastShown || !settings.debugToast) return
        toastShown = true
        try {
            Toast.makeText(
                activity,
                "液态玻璃已注入 ${activity.javaClass.simpleName}",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Throwable) {
        }
    }

    fun reposition() {
        val view = overlay ?: return
        val params = view.layoutParams

        val screenW = activity.resources.displayMetrics.widthPixels
        val screenH = activity.resources.displayMetrics.heightPixels
        val marginH = dp(settings.horizontalMarginDp)
        val barH = dp(settings.barHeightDp)

        if (params !is WindowManager.LayoutParams) {
            (params as? ViewGroup.MarginLayoutParams)?.let {
                it.height = barH
                it.leftMargin = marginH
                it.rightMargin = marginH
                it.bottomMargin = if (settings.manualPlacement) {
                    dp(settings.manualBottomOffsetDp)
                } else {
                    dp(settings.bottomMarginDp)
                }
                view.requestLayout()
            }
            return
        }

        params.width = screenW - marginH * 2
        params.height = barH
        params.x = 0
        // Re-apply blur radius in case settings changed.
        applyBackgroundBlur(params)

        if (settings.manualPlacement) {
            params.y = dp(settings.manualBottomOffsetDp)
        } else {
            val match = NativeBottomBarLocator.locate(activity, 40f, 72f)
            if (match != null) {
                val barCenterY = match.boundsOnScreen.centerY()
                val fromBottom = screenH - barCenterY - barH / 2
                params.y = fromBottom.coerceAtLeast(dp(settings.bottomMarginDp))
                if (settings.hideNativeBar) hideNativeBar(match.view)
            } else {
                params.y = dp(settings.bottomMarginDp)
            }
        }

        try {
            activity.windowManager.updateViewLayout(view, params)
        } catch (t: Throwable) {
            ModuleLog.w("updateViewLayout failed", t)
        }
    }

    private fun hideNativeBar(bar: View) {
        try {
            bar.alpha = 0f
        } catch (t: Throwable) {
            ModuleLog.w("hideNativeBar failed", t)
        }
    }

    fun applySettings(newSettings: ModuleSettings) {
        settings = newSettings
        if (!newSettings.enabled) {
            remove()
            return
        }
        overlay?.applySettings(newSettings)
        reposition()
    }

    fun remove() {
        val view = overlay ?: return
        try {
            when (view.layoutParams) {
                is WindowManager.LayoutParams -> if (attached) activity.windowManager.removeView(view)
                else -> (view.parent as? ViewGroup)?.removeView(view)
            }
        } catch (t: Throwable) {
            ModuleLog.w("removeView failed", t)
        }
        attached = false
        overlay = null
        toastShown = false
    }

    private fun buildLayoutParams(windowType: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = windowType
            format = PixelFormat.TRANSLUCENT
            // Do NOT steal touches: taps pass through to the native tab bar.
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = dp(settings.barHeightDp)
        }
    }
}
