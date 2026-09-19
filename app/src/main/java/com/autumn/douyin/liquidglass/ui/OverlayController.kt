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
 * Owns the lifecycle of one glass overlay for one [Activity]: creates the view,
 * attaches it to the window at the right spot, drives the backdrop capture, and
 * repositions itself. Call [install] once per resumed activity and [remove]
 * when it pauses / is destroyed.
 *
 * Placement has two modes (see [ModuleSettings.manualPlacement]):
 *   - manual : pin to the bottom of the screen. Always works, version-proof.
 *   - auto   : ask [NativeBottomBarLocator] to align with Douyin's real bar.
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

        val capture = DynamicBitmapBackdrop(activity, view)
        backdrop = capture
        view.post {
            reposition()
            capture.start()
            maybeToast()
        }
    }

    /** Try a panel sub-window first, then a plain application window, then a
     * decor-view child as a last resort so *something* always shows. */
    private fun tryAttach(view: LiquidGlassOverlayView): Boolean {
        val token = activity.window?.decorView?.windowToken

        // Strategy 1 & 2: dedicated WindowManager window (no self-capture loop).
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
                activity.windowManager.addView(view, params)
                ModuleLog.i("overlay attached (windowType=$type) to ${activity.javaClass.simpleName}")
                return true
            } catch (t: Throwable) {
                ModuleLog.w("attach windowType=$type failed: ${t.message}")
            }
        }

        // Strategy 3: add straight into the activity content root. This always
        // renders, but PixelCopy of the whole window would then capture our own
        // overlay -> DynamicBitmapBackdrop handles that by excluding via
        // visibility toggling; here we simply accept a slightly softer look.
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
                ModuleLog.i("overlay attached as content child to ${activity.javaClass.simpleName}")
                return true
            }
        } catch (t: Throwable) {
            ModuleLog.w("attach as content child failed: ${t.message}")
        }
        return false
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

        // Content-child attachment uses FrameLayout params, not window params.
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
            // Keep it laid out (so tab clicks still register underneath the
            // non-touchable glass) but invisible.
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
        backdrop?.stop()
        backdrop = null
        val view = overlay ?: return
        try {
            when (val p = view.layoutParams) {
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
