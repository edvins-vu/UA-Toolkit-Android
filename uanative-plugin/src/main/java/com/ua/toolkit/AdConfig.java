package com.ua.toolkit;

import android.content.Intent;
import android.graphics.Color;
import java.io.File;

public class AdConfig {

    // --- Logical Grouping of Defaults and Constraints ---

    private static final class Defaults {
        static final String  ORIENTATION = "landscape";
        static final int     DELAY       = 5;
        static final int     SKIP_DELAY  = 3;

        // GET Button
        static final String  GET_TEXT       = "GET";
        static final String  GET_COLOR      = "#4CAF50"; // Original Green
        static final String  GET_TEXT_COLOR = "#FFFFFF";
        static final int     GET_TEXT_SIZE  = 14;
        static final int     GET_CORNER     = 100;

        // Card
        static final String  CARD_BG_COLOR  = "#80000000"; // Semi-trans black
        static final int     CARD_CORNER     = 100;

        // Reward
        static final String  REWARD_COUNTDOWN = "Reward in: %ds";
        static final String  REWARD_EARNED    = "Reward earned!";
        static final String  OPEN_STORE       = "OPEN STORE";
    }

    private static final class Limits {
        static final int MAX_STR_LEN     = 60;
        static final int MAX_BTN_STR_LEN = 30;

        static final int MIN_TEXT_SIZE   = 10;
        static final int MAX_TEXT_SIZE   = 20;

        static final int MIN_CORNER      = 0;
        static final int MAX_CORNER      = 200;

        static final int MIN_BTN_WIDTH   = 40;
        static final int MAX_BTN_WIDTH   = 250;
        static final int MIN_BTN_HEIGHT  = 20;
        static final int MAX_BTN_HEIGHT  = 100;
    }

    // --- Properties ---

    public final String  videoPath;
    public final String  clickUrl;
    public final boolean isRewarded;
    public final boolean isFlowB;
    public final String  bundleId;
    public final String  orientation;

    public final int     closeButtonDelay;
    public final int     peekDelay;
    public final int     skipButtonDelaySec;
    public final int     pulseStartDelaySec;

    public final String  getButtonText;
    public final String  getButtonColor;
    public final String  getButtonTextColor;
    public final int     getButtonWidthDp;
    public final int     getButtonHeightDp;
    public final int     getButtonTextSizeSp;
    public final int     getButtonCornerRadiusDp;

    public final String  cardBackgroundColor;
    public final int     cardCornerRadiusDp;

    public final boolean disableMuteButton;
    public final boolean disableSkipButton;
    public final boolean disablePulse;
    public final boolean disablePopupBackground;
    public final boolean disableRewardCountdown;

    public final String  rewardCountdownText;
    public final String  rewardEarnedText;
    public final int     rewardTextSizeSp;
    public final String  rewardTextColor;
    public final String  openStoreButtonText;

    // --- Constructor ---

    public AdConfig(
            String videoPath, String clickUrl, boolean isRewarded, boolean isFlowB,
            String bundleId, String orientation, int closeButtonDelay, int peekDelay,
            int skipButtonDelaySec, int pulseStartDelaySec, String getButtonText,
            String getButtonColor, String getButtonTextColor, int getButtonWidthDp,
            int getButtonHeightDp, int getButtonTextSizeSp, int getButtonCornerRadiusDp,
            String cardBackgroundColor, int cardCornerRadiusDp, boolean disableMuteButton,
            boolean disableSkipButton, boolean disablePulse, boolean disablePopupBackground,
            boolean disableRewardCountdown, String rewardCountdownText, String rewardEarnedText,
            int rewardTextSizeSp, String rewardTextColor, String openStoreButtonText
    ) {
        // Core
        this.videoPath   = videoPath;
        this.clickUrl    = (clickUrl != null) ? clickUrl : "";
        this.isRewarded  = isRewarded;
        this.isFlowB     = isFlowB && isRewarded;
        this.bundleId    = (bundleId != null) ? bundleId : "";
        this.orientation = validateOrientation(orientation);

        // Timing
        this.closeButtonDelay   = clamp(closeButtonDelay, 0, 120, Defaults.DELAY);
        this.peekDelay          = clamp(peekDelay, 0, 60, Defaults.DELAY);
        this.skipButtonDelaySec = clamp(skipButtonDelaySec, 0, 60, Defaults.SKIP_DELAY);
        this.pulseStartDelaySec = clamp(pulseStartDelaySec, 0, 120, Defaults.DELAY);

        // GET Button
        this.getButtonText           = validateString(getButtonText, Defaults.GET_TEXT, Limits.MAX_BTN_STR_LEN);
        this.getButtonColor          = validateHex(getButtonColor, Defaults.GET_COLOR);
        this.getButtonTextColor      = validateHex(getButtonTextColor, Defaults.GET_TEXT_COLOR);
        this.getButtonWidthDp        = validateDimension(getButtonWidthDp, Limits.MIN_BTN_WIDTH, Limits.MAX_BTN_WIDTH);
        this.getButtonHeightDp       = validateDimension(getButtonHeightDp, Limits.MIN_BTN_HEIGHT, Limits.MAX_BTN_HEIGHT);
        this.getButtonTextSizeSp     = clamp(getButtonTextSizeSp, Limits.MIN_TEXT_SIZE, Limits.MAX_TEXT_SIZE, Defaults.GET_TEXT_SIZE);
        this.getButtonCornerRadiusDp = clamp(getButtonCornerRadiusDp, Limits.MIN_CORNER, Limits.MAX_CORNER, Defaults.GET_CORNER);

        // Card
        this.cardBackgroundColor = validateHex(cardBackgroundColor, Defaults.CARD_BG_COLOR);
        this.cardCornerRadiusDp  = clamp(cardCornerRadiusDp, Limits.MIN_CORNER, Limits.MAX_CORNER, Defaults.CARD_CORNER);

        // Controls
        this.disableMuteButton      = disableMuteButton;
        this.disableSkipButton      = disableSkipButton;
        this.disablePulse           = disablePulse;
        this.disablePopupBackground = disablePopupBackground;
        this.disableRewardCountdown = disableRewardCountdown;

        // Reward Texts
        this.rewardCountdownText    = validateString(rewardCountdownText, Defaults.REWARD_COUNTDOWN, Limits.MAX_STR_LEN);
        this.rewardEarnedText       = validateString(rewardEarnedText, Defaults.REWARD_EARNED, Limits.MAX_STR_LEN);
        this.rewardTextSizeSp       = clamp(rewardTextSizeSp, Limits.MIN_TEXT_SIZE, Limits.MAX_TEXT_SIZE, Defaults.GET_TEXT_SIZE);
        this.rewardTextColor        = validateHex(rewardTextColor, Defaults.GET_TEXT_COLOR);
        this.openStoreButtonText    = validateString(openStoreButtonText, Defaults.OPEN_STORE, Limits.MAX_BTN_STR_LEN);
    }

    // --- Helper Logic ---

    private static int clamp(int value, int min, int max, int fallback) {
        return (value >= min && value <= max) ? value : fallback;
    }

    private static int validateDimension(int value, int min, int max) {
        if (value == -1) return -1;
        return (value >= min && value <= max) ? value : -1;
    }

    private static String validateOrientation(String value) {
        return "portrait".equals(value) ? "portrait" : Defaults.ORIENTATION;
    }

    private static String validateString(String value, String fallback, int maxLen) {
        if (value == null || value.isEmpty() || value.length() > maxLen) return fallback;
        return value;
    }

    private static String validateHex(String hex, String fallback) {
        if (hex == null || hex.isEmpty()) return fallback;
        String formatted = hex.startsWith("#") ? hex : "#" + hex;
        try {
            Color.parseColor(formatted);
            return formatted;
        } catch (Exception e) {
            return fallback;
        }
    }

    public static AdConfig fromIntent(Intent intent) {
        // ... (The fromIntent implementation remains logically the same,
        // calling the constructor with intent.getExtras)
        return new AdConfig(
                intent.getStringExtra("VIDEO_PATH"),
                intent.getStringExtra("CLICK_URL"),
                intent.getBooleanExtra("IS_REWARDED", false),
                intent.getBooleanExtra("IS_FLOW_B", false),
                intent.getStringExtra("BUNDLE_ID"),
                intent.getStringExtra("ORIENTATION"),
                intent.getIntExtra("CLOSE_BUTTON_DELAY", -1),
                intent.getIntExtra("POPUP_PEEK_DELAY", -1),
                intent.getIntExtra("SKIP_BUTTON_DELAY", -1),
                intent.getIntExtra("PULSE_START_DELAY", -1),
                intent.getStringExtra("GET_BUTTON_TEXT"),
                intent.getStringExtra("GET_BUTTON_COLOR"),
                intent.getStringExtra("GET_BUTTON_TEXT_COLOR"),
                intent.getIntExtra("GET_BUTTON_WIDTH_DP", -1),
                intent.getIntExtra("GET_BUTTON_HEIGHT_DP", -1),
                intent.getIntExtra("GET_BUTTON_TEXT_SIZE_SP", -1),
                intent.getIntExtra("GET_BUTTON_CORNER_DP", -1),
                intent.getStringExtra("CARD_BG_COLOR"),
                intent.getIntExtra("CARD_CORNER_DP", -1),
                intent.getBooleanExtra("DISABLE_MUTE_BUTTON", false),
                intent.getBooleanExtra("DISABLE_SKIP_BUTTON", false),
                intent.getBooleanExtra("DISABLE_PULSE", false),
                intent.getBooleanExtra("DISABLE_POPUP_BACKGROUND", false),
                intent.getBooleanExtra("DISABLE_REWARD_COUNTDOWN", false),
                intent.getStringExtra("REWARD_COUNTDOWN_TEXT"),
                intent.getStringExtra("REWARD_EARNED_TEXT"),
                intent.getIntExtra("REWARD_TEXT_SIZE_SP", -1),
                intent.getStringExtra("REWARD_TEXT_COLOR"),
                intent.getStringExtra("OPEN_STORE_BUTTON_TEXT")
        );
    }

    public boolean isValid() {
        if (videoPath == null || videoPath.isEmpty()) return false;
        File file = new File(videoPath);
        return file.exists() && file.length() > 0;
    }
}