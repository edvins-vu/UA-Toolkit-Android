package com.ua.toolkit.display;

import android.os.Handler;
import android.os.Looper;

/**
 * Manages countdown timers for ad display.
 */
public class AdTimerManager
{
    public interface Listener
    {
        void onCountdownTick(int remainingSeconds);
        void onCountdownComplete();
        void onRewardTimerTick(int remainingSeconds);
    }

    private final Handler handler;
    private final Listener listener;
    private final int closeButtonDelay;
    private final boolean isRewarded;

    private long adStartTime;
    private long pauseTime = 0;
    private boolean closeButtonShown = false;
    private boolean isStarted = false;
    private Runnable updateTask;

    public AdTimerManager(Listener listener, int closeButtonDelay, boolean isRewarded)
    {
        this.handler = new Handler(Looper.getMainLooper());
        this.listener = listener;
        this.closeButtonDelay = closeButtonDelay;
        this.isRewarded = isRewarded;
    }

    public void start()
    {
        if (isStarted) return; // Video re-prepared after surface recreation — don't reset the clock
        isStarted = true;
        adStartTime = System.currentTimeMillis();

        updateTask = new Runnable()
        {
            @Override
            public void run()
            {
                update();
                if (!closeButtonShown || isRewarded)
                {
                    handler.postDelayed(this, 100);
                }
            }
        };

        handler.post(updateTask);
    }

    public void pause()
    {
        pauseTime = System.currentTimeMillis();
        handler.removeCallbacksAndMessages(null);
    }

    public void stop()
    {
        handler.removeCallbacksAndMessages(null);
    }

    public void resume()
    {
        // Adjust adStartTime to account for paused duration
        if (pauseTime > 0)
        {
            long pausedDuration = System.currentTimeMillis() - pauseTime;
            adStartTime += pausedDuration;
            pauseTime = 0;
        }

        if (updateTask != null)
        {
            handler.post(updateTask);
        }
    }

    private void update()
    {
        // For rewarded ads, always notify so reward timer updates based on video position
        if (isRewarded)
        {
            listener.onCountdownTick(0);
            return;
        }

        // For interstitial ads, use countdown logic
        if (!closeButtonShown)
        {
            long elapsedMs = System.currentTimeMillis() - adStartTime;
            int targetMs = closeButtonDelay * 1000;
            int remainingSeconds = Math.max(0, (int) Math.ceil((targetMs - elapsedMs) / 1000.0));

            if (elapsedMs >= targetMs)
            {
                closeButtonShown = true;
                listener.onCountdownComplete();
            }
            else
            {
                listener.onCountdownTick(remainingSeconds);
            }
        }
    }

    public void updateRewardTimer(int videoPosition, int videoDuration)
    {
        if (isRewarded && videoDuration > 0)
        {
            int remaining = Math.max(0, (videoDuration - videoPosition) / 1000);
            listener.onRewardTimerTick(remaining);
        }
    }

    public boolean isCloseButtonShown()
    {
        return closeButtonShown;
    }
}