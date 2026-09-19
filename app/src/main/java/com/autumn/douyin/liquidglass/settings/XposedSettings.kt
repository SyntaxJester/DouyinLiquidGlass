package com.autumn.douyin.liquidglass.settings

import com.autumn.douyin.liquidglass.ModuleLog
import de.robv.android.xposed.XSharedPreferences

/**
 * Reads [ModuleSettings] from the module app's SharedPreferences while running
 * *inside Douyin* via [XSharedPreferences]. Requires the manifest meta-data
 * `xposedsharedprefs=true` and the app to write prefs with MODE_WORLD_READABLE
 * (LSPosed then relocates them to a world-readable path this class can open).
 */
object XposedSettings {

    private const val SELF_PACKAGE = "com.autumn.douyin.liquidglass"

    @Volatile
    private var prefs: XSharedPreferences? = null

    private fun prefs(): XSharedPreferences? {
        val existing = prefs
        if (existing != null) {
            existing.reload()
            return existing
        }
        return try {
            val p = XSharedPreferences(SELF_PACKAGE, SettingsStore.PREFS_NAME)
            p.makeWorldReadable()
            if (!p.file.canRead()) {
                ModuleLog.w("XSharedPreferences not readable at ${p.file.path}; using defaults")
            }
            prefs = p
            p
        } catch (t: Throwable) {
            ModuleLog.w("Failed to open XSharedPreferences", t)
            null
        }
    }

    fun load(): ModuleSettings {
        val p = prefs() ?: return ModuleSettings.DEFAULT
        val d = ModuleSettings.DEFAULT
        return try {
            ModuleSettings(
                enabled = p.getBoolean(SettingsStore.KEY_ENABLED, d.enabled),
                manualPlacement = p.getBoolean(SettingsStore.KEY_MANUAL_PLACEMENT, d.manualPlacement),
                manualBottomOffsetDp = p.getFloat(SettingsStore.KEY_MANUAL_OFFSET, d.manualBottomOffsetDp),
                barHeightDp = p.getFloat(SettingsStore.KEY_BAR_HEIGHT, d.barHeightDp),
                cornerRadiusDp = p.getFloat(SettingsStore.KEY_CORNER, d.cornerRadiusDp),
                horizontalMarginDp = p.getFloat(SettingsStore.KEY_H_MARGIN, d.horizontalMarginDp),
                bottomMarginDp = p.getFloat(SettingsStore.KEY_B_MARGIN, d.bottomMarginDp),
                blurRadiusDp = p.getFloat(SettingsStore.KEY_BLUR, d.blurRadiusDp),
                refractionHeightDp = p.getFloat(SettingsStore.KEY_REFRACT_H, d.refractionHeightDp),
                refractionAmountDp = p.getFloat(SettingsStore.KEY_REFRACT_A, d.refractionAmountDp),
                chromaticAberration = p.getBoolean(SettingsStore.KEY_CHROMATIC, d.chromaticAberration),
                depthEffect = p.getBoolean(SettingsStore.KEY_DEPTH, d.depthEffect),
                tintColor = p.getInt(SettingsStore.KEY_TINT, d.tintColor),
                highlightAlpha = p.getFloat(SettingsStore.KEY_HIGHLIGHT, d.highlightAlpha),
                hideNativeBar = p.getBoolean(SettingsStore.KEY_HIDE_NATIVE, d.hideNativeBar),
                debugToast = p.getBoolean(SettingsStore.KEY_DEBUG_TOAST, d.debugToast)
            )
        } catch (t: Throwable) {
            ModuleLog.w("XSharedPreferences read failed, using defaults", t)
            ModuleSettings.DEFAULT
        }
    }
}
