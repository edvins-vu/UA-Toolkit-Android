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

    public AdConfig(String videoPath, String clickUrl, boolean isRewarded, int closeButtonDelay, String iconPath)
    {
        this.videoPath = videoPath;
        this.clickUrl = clickUrl != null ? clickUrl : "";
        this.isRewarded = isRewarded;
        this.closeButtonDelay = closeButtonDelay;
        this.iconPath = iconPath != null ? iconPath : "";
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
