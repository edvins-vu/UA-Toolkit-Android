package com.ua.toolkit.display;

import android.content.Context;
import android.widget.VideoView;

/**
 * VideoView that always fills its parent exactly, bypassing the native aspect-ratio constraint
 * in VideoView.onMeasure().
 *
 * Problem: Android's stock VideoView overrides onMeasure to clamp its measured dimensions to the
 * video's native aspect ratio — even when MeasureSpec mode is EXACTLY. On a 20:9 device playing
 * a 16:9 video, this produces a 1920×1080 view inside a 2400×1080 screen, leaving 240px black
 * pillars on each side that no window-level flag can remove.
 *
 * Fix: Report the exact parent-provided dimensions unconditionally. The MediaPlayer's
 * VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING (set in AdVideoPlayer) then center-crops the
 * video frames to fill the now full-screen surface — matching the behaviour of CSS object-fit:cover.
 *
 * Result: video always fills the screen edge-to-edge on any aspect ratio device, with equal
 * cropping on both sides when the video and screen ratios differ.
 */
public class FillVideoView extends VideoView
{
    public FillVideoView(Context context)
    {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        // Skip VideoView's built-in aspect-ratio correction and use the exact
        // dimensions the parent provides. Any ratio mismatch is handled by the
        // MediaPlayer renderer via SCALE_TO_FIT_WITH_CROPPING.
        int width  = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        // Guard: if specs are 0 (UNSPECIFIED during early measure passes), fall back to super
        // so layout doesn't collapse. This only happens before the first real layout pass.
        if (width == 0 || height == 0)
        {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        setMeasuredDimension(width, height);
    }
}
