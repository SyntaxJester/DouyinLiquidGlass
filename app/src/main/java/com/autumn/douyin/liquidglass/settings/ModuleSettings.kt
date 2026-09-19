package com.autumn.douyin.liquidglass.settings

/**
 * Tunable parameters for the liquid glass bottom bar. Every value here is a
 * knob you can safely change; they are read once when the overlay is built and
 * again whenever [com.autumn.douyin.liquidglass.ui.LiquidGlassOverlayView.applySettings]
 * is called.
 *
 * All distances are in dp unless noted.
 */
data class ModuleSettings(
    /** Master switch. When false the module installs nothing. */
    val enabled: Boolean = true,

    /** Height of the floating glass bar. Douyin's native bar is ~49dp tall. */
    val barHeightDp: Float = 56f,

    /** Corner radius of the glass pill. */
    val cornerRadiusDp: Float = 28f,

    /** Left/right inset from the screen edges. */
    val horizontalMarginDp: Float = 12f,

    /** Gap between the glass bar and the bottom of the usable screen. */
    val bottomMarginDp: Float = 10f,

    /** Gaussian blur radius applied to the content sampled behind the bar. */
    val blurRadiusDp: Float = 16f,

    /** How far (in px, scaled) light bends at the glass edge. Higher = stronger lens. */
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
    val hideNativeBar: Boolean = true
) {
    companion object {
        val DEFAULT = ModuleSettings()
    }
}
