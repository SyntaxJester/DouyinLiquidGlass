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
 * v1.3 rendering model: NO screen capture. The frosted look is produced by
 *   (a) the system's cross-window blur behind the overlay window
 *       (WindowManager.LayoutParams.setBlurBehindRadius + FLAG_BLUR_BEHIND,
 *        API 31+; blurs whatever is behind — including video SurfaceViews —
 *        and can never sample our own pixels), and
 *   (b) translucent Canvas gradients drawn by [LiquidGlassOverlayView].
 *
 * This eliminates the white-out that PixelCopy caused: Douyin plays video on a
 * hardware SurfaceView that PixelCopy cannot read, so the old capture returned
 * a blank/white frame and we painted white.
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
                enableBlurBehind(params)
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

    /** Turn on system cross-window blur behind our window, when supported. */
    private fun enableBlurBehind(params: WindowManager.LayoutParams) {
        try {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            val supported = activity.windowManager.isCrossWindowBlurEnabled
            if (supported) {
                val px = dp(settings.blurRadiusDp).coerceIn(0, 80)
                params.blurBehindRadius = px
                // A faint scrim so the blur reads even over bright content.
                params.dimAmount = 0.06f
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                ModuleLog.d("cross-window blur enabled, radius=${px}px")
            } else {
                ModuleLog.d("cross-window blur NOT supported on this ROM; translucent-only glass")
            }
        } catch (t: Throwable) {
            ModuleLog.w("enableBlurBehind failed", t)
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
        enableBlurBehind(params)

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
