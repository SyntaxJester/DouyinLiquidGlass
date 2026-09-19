package com.autumn.douyin.liquidglass.settings

/**
 * Tunable parameters for the liquid glass bottom bar. Every value here is a
 * knob the in-app settings screen can change; they are read fresh from
 * SharedPreferences whenever an activity in Douyin resumes.
 *
 * All distances are in dp unless noted.
 */
data class ModuleSettings(
    /** Master switch. When false the module installs nothing. */
    val enabled: Boolean = true,

    /**
     * When true, skip the (fragile) native-bar auto-locate and just pin the
     * glass to the bottom of the screen using [manualBottomOffsetDp]. Default
     * ON so the glass is guaranteed to show up regardless of Douyin version;
     * turn it off once auto-locate is tuned for your build.
     */
    val manualPlacement: Boolean = true,

    /**
     * Gap above the bottom edge when [manualPlacement] is on. Default 0 so the
     * glass sits directly OVER the native tab bar (icons show through it),
     * which is the "glass tab bar" look. Raise it to float the pill above the
     * native bar instead.
     */
    val manualBottomOffsetDp: Float = 0f,

    /**
     * Height of the glass bar. Default 64dp so it covers the native tab strip
     * (icons + labels) on most phones.
     */
    val barHeightDp: Float = 64f,

    /** Corner radius of the glass pill. */
    val cornerRadiusDp: Float = 26f,

    /** Left/right inset from the screen edges. */
    val horizontalMarginDp: Float = 8f,

    /** Gap between the glass bar and the bottom of the usable screen (auto mode). */
    val bottomMarginDp: Float = 10f,

    /** Gaussian blur radius applied to the content sampled behind the bar. */
    val blurRadiusDp: Float = 16f,

    /** How far light bends at the glass edge. Higher = stronger lens. */
    val refractionHeightDp: Float = 12f,
    val refractionAmountDp: Float = 24f,

    /** Split RGB at the edges for a subtle chromatic-aberration rim. */
    val chromaticAberration: Boolean = true,

    /** Depth push so the refraction bulges toward the center. */
    val depthEffect: Boolean = true,

    /** Tint painted over the blurred content. Alpha controls frostiness. */
    val tintColor: Int = 0x1FFFFFFF, // ~12% white

    /** Top rim highlight opacity (0..1). */
    val highlightAlpha: Float = 0.35f,

    /** Hide the native bar behind the overlay so it does not show through. */
    val hideNativeBar: Boolean = true,

    /** Show a Toast inside Douyin when the overlay attaches (debugging aid). */
    val debugToast: Boolean = true
) {
    companion object {
        val DEFAULT = ModuleSettings()
    }
}
