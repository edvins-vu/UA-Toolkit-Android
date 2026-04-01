package com.ua.toolkit.popup;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import com.ua.toolkit.AdConfig;

/**
 * Static styling helpers for AdPopup — handles all colour parsing and drawable construction
 * so that AdPopup itself stays focused on ad-lifecycle logic ("Thin Controller, Fat Helper").
 */
public class AdVisualsHelper
{
    /** Parses a hex colour string, returning {@code fallback} on null, empty, or malformed input. */
    public static int parseColor(String hex, int fallback)
    {
        if (hex == null || hex.isEmpty()) return fallback;
        try { return Color.parseColor(hex); }
        catch (IllegalArgumentException e) { return fallback; }
    }

    /** Builds the pill-card background drawable using {@code config.cardBackgroundColor} and corner radius. */
    static GradientDrawable makeCardBackground(AdConfig config, float cornerRadiusPx)
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(parseColor(
                config != null ? config.cardBackgroundColor : null,
                Color.parseColor("#80000000")));
        bg.setCornerRadius(cornerRadiusPx);
        return bg;
    }

    /** Builds the GET-button background drawable using {@code config.getButtonColor} and corner radius. */
    static GradientDrawable makeButtonBackground(AdConfig config, float cornerRadiusPx)
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(parseColor(
                config != null ? config.getButtonColor : null,
                Color.parseColor("#4CAF50")));
        bg.setCornerRadius(cornerRadiusPx);
        return bg;
    }

    /** Returns the GET-button text colour from config, falling back to {@link Color#WHITE}. */
    static int parseButtonTextColor(AdConfig config)
    {
        if (config == null || config.getButtonTextColor.isEmpty()) return Color.WHITE;
        return parseColor(config.getButtonTextColor, Color.WHITE);
    }

    /**
     * Builds an OVAL (circle) background drawable — used for the mute button.
     * Caller is responsible for converting dp values to px before passing {@code borderWidthPx}.
     */
    public static GradientDrawable makeCircleBackground(int bgColor, int borderColor, float borderWidthPx)
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(bgColor);
        bg.setStroke((int) borderWidthPx, borderColor);
        return bg;
    }

    /**
     * Builds a rounded-rectangle background drawable — used for the close button.
     * Caller is responsible for converting dp values to px before passing {@code cornerRadiusPx}
     * and {@code borderWidthPx}.
     */
    public static GradientDrawable makeRoundedBackground(int bgColor, int borderColor, int cornerRadiusPx, float borderWidthPx)
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(cornerRadiusPx);
        bg.setColor(bgColor);
        bg.setStroke((int) borderWidthPx, borderColor);
        return bg;
    }
}
