package com.ua.toolkit.popup;

import com.ua.toolkit.AdConfig;

/**
 * Layout model for AdPopup — all visual dimensions and animation parameters in one place.
 * Fields hold the original hardcoded defaults; updateFromConfig() overlays AdConfig values
 * where a remote counterpart exists. AdPopup references only this model — no raw numbers.
 */
class AdPopupLayout
{
    // --- Card layout ---
    int cardPaddingHorizontalDp = 10;
    int cardPaddingVerticalDp   = 12;
    int cardEdgeMarginDp        = 24;   // margin from screen edge + nav-bar inset
    int cardCornerRadiusDp      = 100;  // overridable: AdConfig.cardCornerRadiusDp

    // --- Button layout ---
    int buttonWidthDp           = 165;  // overridable: AdConfig.getButtonWidthDp (if > 0)
    int buttonHeightDp          = -1;   // -1 = WRAP_CONTENT; overridable: AdConfig.getButtonHeightDp (if > 0)
    int buttonPaddingVerticalDp = 10;   // applied only when buttonHeightDp == -1
    int buttonCornerRadiusDp    = 100;  // overridable: AdConfig.getButtonCornerRadiusDp
    int buttonTextSizeSp        = 14;   // overridable: AdConfig.getButtonTextSizeSp

    // --- Animation ---
    int   slideInDurationMs  = 280;    // peek slide-in, stage3 slide-in, tap-guard threshold
    int   slideOutDurationMs = 200;    // stage1 slide-off when store opens (distinct from slideIn)
    float pulseScale         = 1.05f;  // continuous GET button pulse peak scale
    int   pulseDurationMs    = 600;    // continuous pulse half-cycle duration
    float tapPulseScale      = 1.04f;  // one-shot card pulse on video tap
    int   tapPulseDurationMs = 350;    // one-shot tap-pulse duration

    // --- Internal safety constants (not overridable from remote) ---
    final int offscreenFallbackDp  = 1000; // initial off-screen Y before first layout pass
    final int cardHeightFallbackDp = 60;   // fallback card height when not yet measured
    final int slideInFallbackDp    = 200;  // stage3 startY when getHeight() returns 0
    final int slideOutMinHeightDp  = 100;  // minimum height used in stage1 slide-off calc

    // --- Flow B transition ---
    final int flowBTransitionDelayMs = 1500; // delay before CLOSE button appears after both flags are true
    final int flowBFadeDurationMs    = 300;  // crossfade animation duration for OPEN_STORE → CLOSE transition

    /**
     * Overlays AdConfig values for fields that have a remote config counterpart.
     * AdConfig already validates all values so no secondary checks are needed.
     * Width and height are only applied when explicitly set (> 0); -1 sentinel keeps the default.
     */
    void updateFromConfig(AdConfig config)
    {
        if (config == null) return;
        cardCornerRadiusDp   = config.cardCornerRadiusDp;
        buttonCornerRadiusDp = config.getButtonCornerRadiusDp;
        buttonTextSizeSp     = config.getButtonTextSizeSp;
        if (config.getButtonWidthDp  > 0) buttonWidthDp  = config.getButtonWidthDp;
        if (config.getButtonHeightDp > 0) buttonHeightDp = config.getButtonHeightDp;
    }
}
