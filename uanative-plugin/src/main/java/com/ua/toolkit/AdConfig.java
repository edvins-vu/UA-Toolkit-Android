package com.ua.toolkit;

import java.io.File;

/**
 * Configuration data for ad display.
 * Dimension fields (width/height) use -1 to mean "not set" — AdPopup renders WRAP_CONTENT.
 * Text size and corner radius fall back to hardcoded defaults when -1.
 * String fields fall back to hardcoded defaults when null/empty.
 */
public class AdConfig
{
    public final String  videoPath;
    public final String  clickUrl;
    public final boolean isRewarded;
    public final int     closeButtonDelay;
    public final int     peekDelay;
    public final String  bundleId;
    public final String  getButtonText;
    public final String  rewardCountdownText;
    public final String  rewardEarnedText;

    // GET button visuals
    public final String  getButtonColor;
    public final String  getButtonTextColor;
    public final int     getButtonWidthDp;
    public final int     getButtonHeightDp;
    public final int     getButtonTextSizeSp;
    public final int     getButtonCornerRadiusDp;

    // Popup card visuals
    public final String  cardBackgroundColor;
    public final int     cardCornerRadiusDp;
    public final String  cardPosition;         // "bottom_end" | "bottom_center" | "bottom_start"

    // Controls
    public final boolean showMuteButton;
    public final boolean showSkipButton;
    public final int     skipButtonDelaySec;

    // Animation
    public final boolean pulseEnabled;
    public final int     pulseStartDelaySec;

    // Reward
    public final boolean showRewardCountdown;
    public final int     rewardTextSizeSp;
    public final String  rewardTextColor;

    // Popup background
    public final boolean showProgressBar;


    public AdConfig(
            // Core
            String  videoPath,
            String  clickUrl,
            boolean isRewarded,

            // Timing
            int     closeButtonDelay,
            int     peekDelay,
            String  bundleId,

            // GET button
            String  getButtonText,
            String  rewardCountdownText,
            String  rewardEarnedText,
            String  getButtonColor,
            String  getButtonTextColor,
            int     getButtonWidthDp,
            int     getButtonHeightDp,
            int     getButtonTextSizeSp,
            int     getButtonCornerRadiusDp,

            // Popup card
            String  cardBackgroundColor,
            int     cardCornerRadiusDp,
            String  cardPosition,

            // Controls
            boolean showMuteButton,
            boolean showSkipButton,
            int     skipButtonDelaySec,
            boolean pulseEnabled,
            int     pulseStartDelaySec,
            boolean showRewardCountdown,
            int     rewardTextSizeSp,
            String  rewardTextColor,
            boolean showProgressBar
    )
    {
        this.videoPath            = videoPath;
        this.clickUrl             = clickUrl             != null ? clickUrl             : "";
        this.isRewarded           = isRewarded;
        this.closeButtonDelay     = closeButtonDelay;
        this.peekDelay            = peekDelay;
        this.bundleId             = bundleId             != null ? bundleId             : "";
        this.getButtonText        = nonEmpty(getButtonText,        "GET");
        this.rewardCountdownText  = nonEmpty(rewardCountdownText,  "Reward in: %ds");
        this.rewardEarnedText     = nonEmpty(rewardEarnedText,     "Reward earned!");

        // GET button — apply fallbacks
        // Width/height: store raw (-1 = not set, AdPopup will use WRAP_CONTENT + padding)
        // TextSize/CornerRadius: apply default when -1/invalid
        this.getButtonColor          = nonEmpty(getButtonColor, "#4CAF50");
        this.getButtonTextColor      = getButtonTextColor != null ? getButtonTextColor : "";
        this.getButtonWidthDp        = getButtonWidthDp;
        this.getButtonHeightDp       = getButtonHeightDp;
        this.getButtonTextSizeSp     = getButtonTextSizeSp     > 0 ? getButtonTextSizeSp     : 14;
        this.getButtonCornerRadiusDp = getButtonCornerRadiusDp >= 0 ? getButtonCornerRadiusDp : 100;

        // Card — apply fallbacks
        this.cardBackgroundColor  = nonEmpty(cardBackgroundColor, "#80000000");
        this.cardCornerRadiusDp   = cardCornerRadiusDp >= 0 ? cardCornerRadiusDp : 100;
        this.cardPosition         = nonEmpty(cardPosition,        "bottom_end");

        // Controls
        this.showMuteButton    = showMuteButton;
        this.showSkipButton    = showSkipButton;
        this.skipButtonDelaySec = skipButtonDelaySec > 0
                ? skipButtonDelaySec
                : Math.max(closeButtonDelay - 3, 0);

        // Animation
        this.pulseEnabled       = pulseEnabled;
        this.pulseStartDelaySec = pulseStartDelaySec > 0 ? pulseStartDelaySec : 5;

        // Reward
        this.showRewardCountdown = showRewardCountdown;
        this.rewardTextSizeSp    = rewardTextSizeSp > 0 ? rewardTextSizeSp : 0;
        this.rewardTextColor     = rewardTextColor != null ? rewardTextColor : "";

        // Popup background
        this.showProgressBar = showProgressBar;
    }

    private static String nonEmpty(String value, String fallback)
    {
        return (value != null && !value.isEmpty()) ? value : fallback;
    }

    public boolean isValid()
    {
        if (videoPath == null || videoPath.isEmpty()) return false;
        File file = new File(videoPath);
        if (!file.exists()) return false;
        if (file.length() == 0) return false;
        return true;
    }
}
