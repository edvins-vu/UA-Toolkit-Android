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
    private final boolean isPlayable;

    private long adStartTime;
    private long pauseTime = 0;
    private boolean closeButtonShown = false;
    private boolean isStarted = false;
    private boolean isRunning = false;
    private boolean rewardEarned = false;
    private Runnable updateTask;

    public AdTimerManager(Listener listener, int closeButtonDelay, boolean isRewarded, boolean isPlayable)
    {
        this.handler = new Handler(Looper.getMainLooper());
        this.listener = listener;
        this.closeButtonDelay = closeButtonDelay;
        this.isRewarded = isRewarded;
        this.isPlayable = isPlayable;
    }

    public void start()
    {
        if (isStarted) return; // Video re-prepared after surface recreation — don't reset the clock
        isStarted = true;
        isRunning = true;
        adStartTime = System.currentTimeMillis();

        updateTask = new Runnable()
        {
            @Override
            public void run()
            {
                update();
                if (!closeButtonShown || (isRewarded && !rewardEarned))
                {
                    handler.postDelayed(this, 100);
                }
                else
                {
                    isRunning = false;
                }
            }
        };

        handler.post(updateTask);
    }

    public void pause()
    {
        pauseTime = System.currentTimeMillis();
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
    }

    public void stop()
    {
        isRunning = false;
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

        if (updateTask != null && isStarted && !isRunning)
        {
            isRunning = true;
            handler.post(updateTask);
        }
    }

    private void update()
    {
        // Rewarded video: poll video position so AdActivity can drive the countdown UI.
        // Reward is triggered externally when the video completes via markRewardEarned().
        // Playable rewarded uses the elapsed-time path below — reward is tied to timer elapsed,
        // not video completion, so it follows the same engagement-gate logic as interstitial.
        if (isRewarded && !isPlayable)
        {
            listener.onCountdownTick(0);
            return;
        }

        // Interstitial and playable (rewarded or not): elapsed-time countdown.
        if (!closeButtonShown)
        {
            long elapsedMs = System.currentTimeMillis() - adStartTime;
            int targetMs = closeButtonDelay * 1000;
            int remainingSeconds = Math.max(0, (int) Math.ceil((targetMs - elapsedMs) / 1000.0));

            if (elapsedMs >= targetMs)
            {
                closeButtonShown = true;
                // Rewarded playable: reward earned when engagement timer elapses (no video completion event).
                if (isPlayable && isRewarded) rewardEarned = true;
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

    public void markRewardEarned()
    {
        rewardEarned = true;
    }

    public boolean isCloseButtonShown()
    {
        return closeButtonShown;
    }
}
