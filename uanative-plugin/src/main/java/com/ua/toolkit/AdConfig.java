package com.ua.toolkit;

import java.io.File;

/**
 * Configuration data for ad display.
 */
public class AdConfig
{
    public final String videoPath;
    public final String clickUrl;
    public final boolean isRewarded;
    public final int closeButtonDelay;
    public final String iconPath;
    public final int peekDelay;
    public final String bundleId;            // target app package name for the Play Store half-sheet
    public final String appName;             // display name shown in the Stage 1/3 card
    public final String getButtonText;       // label on the GET button (e.g. localised "GET")
    public final String rewardCountdownText; // format string for countdown, %d = seconds (e.g. "Reward in: %ds")
    public final String rewardEarnedText;    // shown when reward is fully earned (e.g. "Reward earned!")

    public AdConfig(String videoPath, String clickUrl, boolean isRewarded, int closeButtonDelay,
                    String iconPath, int peekDelay, String bundleId, String appName,
                    String getButtonText, String rewardCountdownText, String rewardEarnedText)
    {
        this.videoPath           = videoPath;
        this.clickUrl            = clickUrl  != null ? clickUrl  : "";
        this.isRewarded          = isRewarded;
        this.closeButtonDelay    = closeButtonDelay;
        this.iconPath            = iconPath  != null ? iconPath  : "";
        this.peekDelay           = peekDelay;
        this.bundleId            = bundleId  != null ? bundleId  : "";
        this.appName             = appName   != null ? appName   : "";
        this.getButtonText       = nonEmpty(getButtonText,       "GET");
        this.rewardCountdownText = nonEmpty(rewardCountdownText, "Reward in: %ds");
        this.rewardEarnedText    = nonEmpty(rewardEarnedText,    "Reward earned!");
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

    public boolean hasValidIcon()
    {
        if (iconPath == null || iconPath.isEmpty()) return false;
        File file = new File(iconPath);
        return file.exists() && file.length() > 0;
    }
}
