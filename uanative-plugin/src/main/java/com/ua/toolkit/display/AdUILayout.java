package com.ua.toolkit.display;

import android.graphics.Color;

/**
 * Layout model for AdUIManager — all overlay UI dimensions and style constants in one place.
 * No remote config counterparts exist for any of these values; they are pure hardcoded defaults.
 * AdUIManager references only this model — no raw numbers in the controller.
 */
class AdUILayout
{
    // --- Mute button ---
    int muteButtonSizeDp       = 28;
    int muteButtonPaddingDp    = 6;
    int muteButtonTopMarginDp  = 8;
    int muteButtonLeftMarginDp = 20;

    // --- Reward timer pill ---
    String timerBackgroundColor     = "#80000000";
    int    timerCornerRadiusDp      = 23;
    int    timerPaddingHorizontalDp = 12;
    int    timerPaddingVerticalDp   = 6;
    int    timerTopMarginDp         = 8;

    // --- Button container (top-right: skip + close) ---
    int buttonContainerHeightDp    = 28;
    int buttonContainerTopMarginDp = 8;
    int buttonContainerRightDp     = 28;  // initial right margin + applyInsets right padding

    // --- Skip button ---
    int skipButtonSizeDp     = 28;
    int skipButtonTextSizeSp = 15;
    int skipButtonTextColor  = Color.argb(150, 255, 255, 255);

    // --- Close button ---
    int closeButtonSizeDp         = 20;
    int closeButtonTextSizeSp     = 10;
    int closeButtonCornerRadiusDp = 8;

    // --- Shared button background — used by both mute (circle) and close (rounded rect) ---
    int   buttonBgColor       = Color.argb(191, 38, 38, 38);
    float buttonBorderWidthDp = 0.75f;
    int   buttonBorderColor   = Color.argb(51, 255, 255, 255);

    // --- Inset handling ---
    int minTopInsetDp = 8;  // Math.max floor applied to savedTopInset in applyInsets()
}
