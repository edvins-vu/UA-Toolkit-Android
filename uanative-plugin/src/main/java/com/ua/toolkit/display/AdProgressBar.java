package com.ua.toolkit.display;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Thin horizontal bar at the very bottom of the ad that fills left-to-right
 * over the close-button countdown. Fades out when the countdown completes.
 *
 * Drive with setProgress(float 0..1) from the AdTimerManager tick (every 100ms),
 * and call completeAndFade() when the close button is earned.
 */
public class AdProgressBar
{
    private static final String TAG = "AdProgressBar";

    private final Activity     _activity;
    private final FrameLayout  _root;
    private       FrameLayout  _track;
    private       FrameLayout  _fill;
    private       ValueAnimator _completeAnimator;

    public AdProgressBar(Activity activity, FrameLayout root, String color, int heightDp)
    {
        _activity = activity;
        _root     = root;

        int parsedColor;
        try { parsedColor = Color.parseColor(color); }
        catch (IllegalArgumentException e)
        {
            Log.w(TAG, "Invalid progress bar color '" + color + "', falling back to white");
            parsedColor = Color.WHITE;
        }

        int heightPx = dpToPx(heightDp > 0 ? heightDp : 3);

        // Track — full width, semi-transparent version of the bar color
        _track = new FrameLayout(activity);
        FrameLayout.LayoutParams trackLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, heightPx, Gravity.BOTTOM);
        _track.setLayoutParams(trackLp);
        int trackColor = Color.argb(80,
                Color.red(parsedColor), Color.green(parsedColor), Color.blue(parsedColor));
        _track.setBackgroundColor(trackColor);

        // Fill — starts at 0 width, grows to full width
        _fill = new FrameLayout(activity);
        LinearLayout.LayoutParams fillLp = new LinearLayout.LayoutParams(0, heightPx);
        _fill.setLayoutParams(fillLp);
        _fill.setBackgroundColor(parsedColor);

        _track.addView(_fill);
        _root.addView(_track);
    }

    /**
     * Update the fill width. Call every 100ms from AdActivity.onCountdownTick.
     * @param fraction 0.0 = empty, 1.0 = full
     */
    public void setProgress(float fraction)
    {
        if (_track == null || _fill == null) return;
        int trackWidth = _track.getWidth();
        if (trackWidth <= 0) return;

        fraction = Math.max(0f, Math.min(1f, fraction));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) _fill.getLayoutParams();
        lp.width = (int) (trackWidth * fraction);
        _fill.setLayoutParams(lp);
    }

    /**
     * Snap fill to full width then fade the entire bar out over 300ms.
     * Called from AdActivity when the close button is earned.
     */
    public void completeAndFade()
    {
        if (_track == null || _fill == null) return;

        // Cancel any in-progress animator
        if (_completeAnimator != null) { _completeAnimator.cancel(); _completeAnimator = null; }

        // Snap fill to full width
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) _fill.getLayoutParams();
        lp.width = _track.getWidth();
        _fill.setLayoutParams(lp);

        // Fade out track (which contains fill)
        _completeAnimator = ValueAnimator.ofFloat(1f, 0f);
        _completeAnimator.setDuration(300);
        _completeAnimator.setInterpolator(new LinearInterpolator());
        _completeAnimator.addUpdateListener(anim ->
        {
            float alpha = (float) anim.getAnimatedValue();
            if (_track != null) _track.setAlpha(alpha);
        });
        _completeAnimator.addListener(new android.animation.AnimatorListenerAdapter()
        {
            @Override public void onAnimationEnd(android.animation.Animator animation)
            {
                if (_track != null && _root != null)
                {
                    _root.removeView(_track);
                    _track = null;
                    _fill  = null;
                }
            }
        });
        _completeAnimator.start();
    }

    /** Remove bar from root immediately without animation (used on ad cancel/destroy). */
    public void cancel()
    {
        if (_completeAnimator != null) { _completeAnimator.cancel(); _completeAnimator = null; }
        if (_track != null && _root != null) _root.removeView(_track);
        _track = null;
        _fill  = null;
    }

    private int dpToPx(float dp)
    {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                _activity.getResources().getDisplayMetrics());
    }
}
