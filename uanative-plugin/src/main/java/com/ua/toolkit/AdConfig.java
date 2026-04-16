package com.ua.toolkit;

import android.content.Intent;
import android.graphics.Color;

import java.io.File;

/**
 * Configuration data for ad display.
 * Dimension fields (width/height) use -1 to mean "not set" — AdPopup renders WRAP_CONTENT.
 * Text sizes and corner radii are clamped to sane ranges; fall back to hardcoded defaults.
 * Timing delays are clamped to [0, max] ranges; fall back to hardcoded defaults.
 * Color strings are hex-validated; malformed values fall back to hardcoded defaults.
 * Text strings are length-capped; strings exceeding the cap fall back to hardcoded defaults.
 */
public class AdConfig
{
    // Core
    public final String  videoPath;
    public final String  clickUrl;
    public final boolean isRewarded;
    public final boolean isFlowB;
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
    public final String  openStoreButtonText;


    public AdConfig(
            // Core
            String  videoPath,
            String  clickUrl,
            boolean isRewarded,
            boolean isFlowB,
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
            boolean disableRewardCountdown,

            // Reward texts
            String  rewardCountdownText,
            String  rewardEarnedText,
            int     rewardTextSizeSp,
            String  rewardTextColor,
            String  openStoreButtonText
    )
    {
        // Core
        this.videoPath  = videoPath;
        this.clickUrl   = clickUrl  != null ? clickUrl  : "";
        this.isRewarded = isRewarded;
        this.isFlowB    = isFlowB && isRewarded; // Flow B is only valid for rewarded ads
        this.bundleId   = bundleId  != null ? bundleId  : "";

        // Timing
        this.closeButtonDelay   = (closeButtonDelay   >= 0 && closeButtonDelay   <= 120) ? closeButtonDelay   : 5;
        this.peekDelay          = (peekDelay          >= 0 && peekDelay          <= 60)  ? peekDelay          : 5;
        this.skipButtonDelaySec = (skipButtonDelaySec >= 0 && skipButtonDelaySec <= 60)  ? skipButtonDelaySec : 3;
        this.pulseStartDelaySec = (pulseStartDelaySec >= 0 && pulseStartDelaySec <= 120) ? pulseStartDelaySec : 5;

        // GET button — apply fallbacks
        // Width/height: -1 = not set (AdPopup uses WRAP_CONTENT); reject implausibly small or large values
        this.getButtonText           = clampedString(getButtonText, "GET", 30);
        this.getButtonColor          = validateHex(getButtonColor,     "#4CAF50");
        this.getButtonTextColor      = validateHex(getButtonTextColor, "#FFFFFF");
        this.getButtonWidthDp        = (getButtonWidthDp  == -1 || (getButtonWidthDp  >= 40  && getButtonWidthDp  <= 500)) ? getButtonWidthDp  : -1;
        this.getButtonHeightDp       = (getButtonHeightDp == -1 || (getButtonHeightDp >= 20  && getButtonHeightDp <= 200)) ? getButtonHeightDp : -1;
        this.getButtonTextSizeSp     = (getButtonTextSizeSp >= 10 && getButtonTextSizeSp <= 40) ? getButtonTextSizeSp : 14;
        this.getButtonCornerRadiusDp = (getButtonCornerRadiusDp >= 0 && getButtonCornerRadiusDp <= 200) ? getButtonCornerRadiusDp : 100;

        // Popup card — apply fallbacks
        this.cardBackgroundColor = validateHex(cardBackgroundColor, "#80000000");
        this.cardCornerRadiusDp  = (cardCornerRadiusDp >= 0 && cardCornerRadiusDp <= 200) ? cardCornerRadiusDp : 100;

        // Controls
        this.disableMuteButton      = disableMuteButton;
        this.disableSkipButton      = disableSkipButton;
        this.disablePulse           = disablePulse;
        this.disablePopupBackground = disablePopupBackground;
        this.disableRewardCountdown = disableRewardCountdown;

        // Reward texts
        this.rewardCountdownText    = clampedString(rewardCountdownText, "Reward in: %ds", 60);
        this.rewardEarnedText       = clampedString(rewardEarnedText,    "Reward earned!", 60);
        this.rewardTextSizeSp       = (rewardTextSizeSp >= 10 && rewardTextSizeSp <= 40) ? rewardTextSizeSp : 14;
        this.rewardTextColor        = validateHex(rewardTextColor, "#FFFFFF");
        this.openStoreButtonText    = clampedString(openStoreButtonText, "OPEN STORE", 30);
    }

    private static String clampedString(String value, String fallback, int maxLen)
    {
        if (value == null || value.isEmpty()) return fallback;
        return value.length() <= maxLen ? value : fallback;
    }

    private static String validateHex(String hex, String fallback)
    {
        if (hex == null || hex.isEmpty()) return fallback;
        String formatted = hex.startsWith("#") ? hex : "#" + hex;
        try {
            Color.parseColor(formatted);
            return formatted;
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Reads all ad configuration fields from an Activity Intent and constructs an AdConfig.
     * IS_PLAYABLE is NOT read here — it is a runtime flag owned by AdActivity, not a config field.
     */
    public static AdConfig fromIntent(Intent intent)
    {
        return new AdConfig(
                // Core
                intent.getStringExtra("VIDEO_PATH"),
                intent.getStringExtra("CLICK_URL"),
                intent.getBooleanExtra("IS_REWARDED", false),
                intent.getBooleanExtra("IS_FLOW_B", false),
                intent.getStringExtra("BUNDLE_ID"),

                // Timing
                intent.getIntExtra("CLOSE_BUTTON_DELAY", -1),
                intent.getIntExtra("POPUP_PEEK_DELAY", -1),
                intent.getIntExtra("SKIP_BUTTON_DELAY", -1),
                intent.getIntExtra("PULSE_START_DELAY", -1),

                // GET button
                intent.getStringExtra("GET_BUTTON_TEXT"),
                intent.getStringExtra("GET_BUTTON_COLOR"),
                intent.getStringExtra("GET_BUTTON_TEXT_COLOR"),
                intent.getIntExtra("GET_BUTTON_WIDTH_DP", -1),
                intent.getIntExtra("GET_BUTTON_HEIGHT_DP", -1),
                intent.getIntExtra("GET_BUTTON_TEXT_SIZE_SP", -1),
                intent.getIntExtra("GET_BUTTON_CORNER_DP", -1),

                // Popup card
                intent.getStringExtra("CARD_BG_COLOR"),
                intent.getIntExtra("CARD_CORNER_DP", -1),

                // Controls
                intent.getBooleanExtra("DISABLE_MUTE_BUTTON", false),
                intent.getBooleanExtra("DISABLE_SKIP_BUTTON", false),
                intent.getBooleanExtra("DISABLE_PULSE", false),
                intent.getBooleanExtra("DISABLE_POPUP_BACKGROUND", false),
                intent.getBooleanExtra("DISABLE_REWARD_COUNTDOWN", false),

                // Reward texts
                intent.getStringExtra("REWARD_COUNTDOWN_TEXT"),
                intent.getStringExtra("REWARD_EARNED_TEXT"),
                intent.getIntExtra("REWARD_TEXT_SIZE_SP", -1),
                intent.getStringExtra("REWARD_TEXT_COLOR"),
                intent.getStringExtra("OPEN_STORE_BUTTON_TEXT")
        );
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
