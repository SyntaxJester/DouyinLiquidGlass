package com.autumn.douyin.liquidglass.ui

import android.app.Activity
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.autumn.douyin.liquidglass.ModuleLog
import com.autumn.douyin.liquidglass.nativebar.NativeBottomBarLocator
import com.autumn.douyin.liquidglass.settings.ModuleSettings

/**
 * Owns the lifecycle of one glass overlay for one [Activity]: creates the view,
 * attaches it to the window at the right spot, drives the backdrop capture, and
 * repositions itself over the native bar. Call [install] once per resumed
 * activity and [remove] when it pauses / is destroyed.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class OverlayController(
    private val activity: Activity,
    private var settings: ModuleSettings
) {

    private val density = activity.resources.displayMetrics.density
    private var overlay: LiquidGlassOverlayView? = null
    private var backdrop: DynamicBitmapBackdrop? = null
    private var attached = false

    private fun dp(v: Float) = (v * density).toInt()

    fun install() {
        if (attached) return
        val view = LiquidGlassOverlayView(activity, settings)
        overlay = view

        val params = buildLayoutParams()
        // A panel sub-window must carry the host window's token, else addView
        // throws BadTokenException.
        params.token = activity.window?.decorView?.windowToken
        try {
            activity.windowManager.addView(view, params)
            attached = true
            ModuleLog.d("overlay attached to ${activity.javaClass.simpleName}")
        } catch (t: Throwable) {
            ModuleLog.e("failed to attach overlay", t)
            return
        }

        val capture = DynamicBitmapBackdrop(activity, view)
        backdrop = capture
        // Delay first capture until the view has a real size.
        view.post {
            reposition()
            capture.start()
        }
    }

    fun reposition() {
        val view = overlay ?: return
        val match = NativeBottomBarLocator.locate(
            activity,
            minBarDp = 40f,
            maxBarDp = 72f
        )

        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val screenW = activity.resources.displayMetrics.widthPixels

        val marginH = dp(settings.horizontalMarginDp)
        params.width = screenW - marginH * 2
        params.height = dp(settings.barHeightDp)
        params.x = 0

        if (match != null) {
            // Align vertically with the native bar, biased to its center.
            val screenH = activity.resources.displayMetrics.heightPixels
            val barCenterY = match.boundsOnScreen.centerY()
            val fromBottom = screenH - barCenterY - params.height / 2
            params.y = fromBottom.coerceAtLeast(dp(settings.bottomMarginDp))
            if (settings.hideNativeBar) {
                hideNativeBar(match.view)
            }
        } else {
            params.y = dp(settings.bottomMarginDp)
        }

        try {
            activity.windowManager.updateViewLayout(view, params)
        } catch (t: Throwable) {
            ModuleLog.w("updateViewLayout failed", t)
        }
    }

    private fun hideNativeBar(bar: View) {
        try {
            // Make the native bar invisible but keep it laid out so tab clicks
            // still register underneath the glass (the overlay is
            // non-touchable, so touches fall through to the native bar).
            bar.alpha = 0f
        } catch (t: Throwable) {
            ModuleLog.w("hideNativeBar failed", t)
        }
    }

    fun applySettings(newSettings: ModuleSettings) {
        settings = newSettings
        overlay?.applySettings(newSettings)
        reposition()
    }

    fun remove() {
        backdrop?.stop()
        backdrop = null
        val view = overlay ?: return
        try {
            if (attached) activity.windowManager.removeView(view)
        } catch (t: Throwable) {
            ModuleLog.w("removeView failed", t)
        }
        attached = false
        overlay = null
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            format = PixelFormat.TRANSLUCENT
            // Do NOT steal touches: FLAG_NOT_TOUCHABLE lets taps pass through to
            // the native tab bar sitting beneath the transparent glass.
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
