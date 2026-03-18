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
    // Core
    public final String  videoPath;
    public final String  clickUrl;
    public final boolean isRewarded;
    public final String  bundleId;

    // Timing
    public final int     closeButtonDelay;
    public final int     peekDelay;

    // GET button
    public final String  getButtonText;
    public final String  getButtonColor;
    public final String  getButtonTextColor;
    public final int     getButtonWidthDp;
    public final int     getButtonHeightDp;
    public final int     getButtonTextSizeSp;
    public final int     getButtonCornerRadiusDp;

    // Popup card
    public final String  cardBackgroundColor;
    public final int     cardCornerRadiusDp;

    // Controls
    public final boolean disableMuteButton;
    public final boolean disableSkipButton;
    public final int     skipButtonDelaySec;
    public final boolean disablePulse;
    public final int     pulseStartDelaySec;
    public final boolean disablePopupBackground;

    // Reward texts
    public final String  rewardCountdownText;
    public final String  rewardEarnedText;
    public final boolean disableRewardCountdown;
    public final int     rewardTextSizeSp;
    public final String  rewardTextColor;


    public AdConfig(
            // Core
            String  videoPath,
            String  clickUrl,
            boolean isRewarded,
            String  bundleId,

            // Timing
            int     closeButtonDelay,
            int     peekDelay,
            int     skipButtonDelaySec,
            int     pulseStartDelaySec,

            // GET button
            String  getButtonText,
            String  getButtonColor,
            String  getButtonTextColor,
            int     getButtonWidthDp,
            int     getButtonHeightDp,
            int     getButtonTextSizeSp,
            int     getButtonCornerRadiusDp,

            // Popup card
            String  cardBackgroundColor,
            int     cardCornerRadiusDp,

            // Controls
            boolean disableMuteButton,
            boolean disableSkipButton,
            boolean disablePulse,
            boolean disablePopupBackground,

            // Reward texts
            String  rewardCountdownText,
            String  rewardEarnedText,
            boolean disableRewardCountdown,
            int     rewardTextSizeSp,
            String  rewardTextColor
    )
    {
        // Core
        this.videoPath  = videoPath;
        this.clickUrl   = clickUrl  != null ? clickUrl  : "";
        this.isRewarded = isRewarded;
        this.bundleId   = bundleId  != null ? bundleId  : "";

        // Timing
        this.closeButtonDelay = closeButtonDelay;
        this.peekDelay        = peekDelay;
        this.skipButtonDelaySec    = skipButtonDelaySec > 0
                ? skipButtonDelaySec
                : Math.max(closeButtonDelay - 3, 0);
        this.pulseStartDelaySec    = pulseStartDelaySec > 0 ? pulseStartDelaySec : 5;

        // GET button — apply fallbacks
        // Width/height: store raw (-1 = not set, AdPopup will use WRAP_CONTENT + padding)
        // TextSize/CornerRadius: apply default when -1/invalid
        this.getButtonText           = nonEmpty(getButtonText, "GET");
        this.getButtonColor          = nonEmpty(getButtonColor, "#4CAF50");
        this.getButtonWidthDp        = getButtonWidthDp;
        this.getButtonHeightDp       = getButtonHeightDp;
        this.getButtonTextSizeSp     = getButtonTextSizeSp     > 0 ? getButtonTextSizeSp     : 14;
        this.getButtonCornerRadiusDp = getButtonCornerRadiusDp >= 0 ? getButtonCornerRadiusDp : 100;
        this.getButtonTextColor      = getButtonTextColor != null ? getButtonTextColor : "#FFFFFF";

        // Popup card — apply fallbacks
        this.cardBackgroundColor = nonEmpty(cardBackgroundColor, "#80000000");
        this.cardCornerRadiusDp  = cardCornerRadiusDp >= 0 ? cardCornerRadiusDp : 100;

        // Controls
        this.disableMuteButton     = disableMuteButton;
        this.disableSkipButton     = disableSkipButton;
        this.disablePulse          = disablePulse;
        this.disablePopupBackground = disablePopupBackground;

        // Reward texts
        this.rewardCountdownText     = nonEmpty(rewardCountdownText, "Reward in: %ds");
        this.rewardEarnedText        = nonEmpty(rewardEarnedText,    "Reward earned!");
        this.disableRewardCountdown  = disableRewardCountdown;
        this.rewardTextSizeSp     = rewardTextSizeSp > 0 ? rewardTextSizeSp : 14;
        this.rewardTextColor      = rewardTextColor != null ? rewardTextColor : "#FFFFFF";
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
