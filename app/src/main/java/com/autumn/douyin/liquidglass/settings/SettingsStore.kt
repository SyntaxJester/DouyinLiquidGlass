package com.autumn.douyin.liquidglass.settings

import android.content.Context
import android.content.SharedPreferences
import com.autumn.douyin.liquidglass.ModuleLog

/**
 * Persists [ModuleSettings] as SharedPreferences and bridges them across
 * processes.
 *
 * The module's own app writes here; the hook (running inside Douyin) reads the
 * same values through [com.autumn.douyin.liquidglass.settings.XposedSettings]
 * using XSharedPreferences.
 *
 * For the cross-process read to work with modern LSPosed, the manifest declares
 * `<meta-data android:name="xposedsharedprefs" android:value="true"/>` and the
 * app opens prefs with [Context.MODE_WORLD_READABLE]. LSPosed intercepts that
 * and stores them in a world-readable location.
 */
object SettingsStore {

    const val PREFS_NAME = "liquid_glass_settings"

    const val KEY_ENABLED = "enabled"
    const val KEY_MANUAL_PLACEMENT = "manual_placement"
    const val KEY_MANUAL_OFFSET = "manual_bottom_offset_dp"
    const val KEY_BAR_HEIGHT = "bar_height_dp"
    const val KEY_CORNER = "corner_radius_dp"
    const val KEY_H_MARGIN = "horizontal_margin_dp"
    const val KEY_B_MARGIN = "bottom_margin_dp"
    const val KEY_BLUR = "blur_radius_dp"
    const val KEY_REFRACT_H = "refraction_height_dp"
    const val KEY_REFRACT_A = "refraction_amount_dp"
    const val KEY_CHROMATIC = "chromatic_aberration"
    const val KEY_DEPTH = "depth_effect"
    const val KEY_TINT = "tint_color"
    const val KEY_HIGHLIGHT = "highlight_alpha"
    const val KEY_HIDE_NATIVE = "hide_native_bar"
    const val KEY_DEBUG_TOAST = "debug_toast"

    @Suppress("DEPRECATION")
    fun openAppPrefs(context: Context): SharedPreferences {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (t: Throwable) {
            // No LSPosed prefs bridge in this process: fall back to a private
            // store so the UI still works (hook just won't see live changes).
            ModuleLog.w("MODE_WORLD_READABLE unavailable, using MODE_PRIVATE", t)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun load(prefs: SharedPreferences): ModuleSettings {
        val d = ModuleSettings.DEFAULT
        return ModuleSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, d.enabled),
            manualPlacement = prefs.getBoolean(KEY_MANUAL_PLACEMENT, d.manualPlacement),
            manualBottomOffsetDp = prefs.getFloat(KEY_MANUAL_OFFSET, d.manualBottomOffsetDp),
            barHeightDp = prefs.getFloat(KEY_BAR_HEIGHT, d.barHeightDp),
            cornerRadiusDp = prefs.getFloat(KEY_CORNER, d.cornerRadiusDp),
            horizontalMarginDp = prefs.getFloat(KEY_H_MARGIN, d.horizontalMarginDp),
            bottomMarginDp = prefs.getFloat(KEY_B_MARGIN, d.bottomMarginDp),
            blurRadiusDp = prefs.getFloat(KEY_BLUR, d.blurRadiusDp),
            refractionHeightDp = prefs.getFloat(KEY_REFRACT_H, d.refractionHeightDp),
            refractionAmountDp = prefs.getFloat(KEY_REFRACT_A, d.refractionAmountDp),
            chromaticAberration = prefs.getBoolean(KEY_CHROMATIC, d.chromaticAberration),
            depthEffect = prefs.getBoolean(KEY_DEPTH, d.depthEffect),
            tintColor = prefs.getInt(KEY_TINT, d.tintColor),
            highlightAlpha = prefs.getFloat(KEY_HIGHLIGHT, d.highlightAlpha),
            hideNativeBar = prefs.getBoolean(KEY_HIDE_NATIVE, d.hideNativeBar),
            debugToast = prefs.getBoolean(KEY_DEBUG_TOAST, d.debugToast)
        )
    }

    fun save(prefs: SharedPreferences, s: ModuleSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, s.enabled)
            .putBoolean(KEY_MANUAL_PLACEMENT, s.manualPlacement)
            .putFloat(KEY_MANUAL_OFFSET, s.manualBottomOffsetDp)
            .putFloat(KEY_BAR_HEIGHT, s.barHeightDp)
            .putFloat(KEY_CORNER, s.cornerRadiusDp)
            .putFloat(KEY_H_MARGIN, s.horizontalMarginDp)
            .putFloat(KEY_B_MARGIN, s.bottomMarginDp)
            .putFloat(KEY_BLUR, s.blurRadiusDp)
            .putFloat(KEY_REFRACT_H, s.refractionHeightDp)
            .putFloat(KEY_REFRACT_A, s.refractionAmountDp)
            .putBoolean(KEY_CHROMATIC, s.chromaticAberration)
            .putBoolean(KEY_DEPTH, s.depthEffect)
            .putInt(KEY_TINT, s.tintColor)
            .putFloat(KEY_HIGHLIGHT, s.highlightAlpha)
            .putBoolean(KEY_HIDE_NATIVE, s.hideNativeBar)
            .putBoolean(KEY_DEBUG_TOAST, s.debugToast)
            .apply()
    }
}
