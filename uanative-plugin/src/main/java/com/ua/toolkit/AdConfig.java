package com.ua.toolkit;

/**
 * Configuration data for ad display.
 */
public class AdConfig
{
    public final String videoPath;
    public final String clickUrl;
    public final boolean isRewarded;
    public final int closeButtonDelay;

    public AdConfig(String videoPath, String clickUrl, boolean isRewarded, int closeButtonDelay)
    {
        this.videoPath = videoPath;
        this.clickUrl = clickUrl != null ? clickUrl : "";
        this.isRewarded = isRewarded;
        this.closeButtonDelay = closeButtonDelay;
    }

    public boolean isValid()
    {
        return videoPath != null && !videoPath.isEmpty();
    }
}