package com.ua.toolkit;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.ua.toolkit.display.AdAudioManager;
import com.ua.toolkit.display.AdTimerManager;
import com.ua.toolkit.display.AdUIManager;
import com.ua.toolkit.display.AdVideoPlayer;
import com.ua.toolkit.popup.AdPopup;

import java.lang.ref.WeakReference;

public class AdActivity extends Activity implements
        AdUIManager.Listener,
        AdVideoPlayer.Listener,
        AdTimerManager.Listener
{
    private static final String TAG = "AdActivity";
    private static final int PREPARE_TIMEOUT_MS = 15_000;
    public static AdCallback callback;
    private static WeakReference<AdActivity> currentInstanceRef;
    private AdUIManager uiManager;
    private AdVideoPlayer videoPlayer;
    private AdAudioManager audioManager;
    private AdTimerManager timerManager;
    private AdPopup popup;
    private AdConfig config;
    private boolean isFullyWatched = false;
    private boolean resultSent = false;
    private boolean pausedByAudioFocus = false;
    private boolean closeButtonEarned = false;
    private OnBackInvokedCallback backCallback; // API 33+

    private final Handler prepareWatchdog = new Handler(Looper.getMainLooper());
    private final Runnable prepareTimeoutRunnable = () -> {
        Log.e(TAG, "Video prepare timed out after " + PREPARE_TIMEOUT_MS + "ms — MediaPlayer fired neither onPrepared nor onError");
        if (callback != null) callback.onAdFailed("Video prepare timed out");
        finishWithResult(false);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        currentInstanceRef = new WeakReference<>(this);

        lockOrientationToCurrentRotation();
        setupWindowFlags();
        parseIntentConfig();

        if (!config.isValid())
        {
            String errorMsg = "Invalid config - video file is missing, empty, or path is null"
                    + (config.videoPath != null ? ": " + config.videoPath : "");
            Log.e(TAG, errorMsg);
            if (callback != null) callback.onAdFailed(errorMsg);
            finishWithResult(false);
            return;
        }

        initializeManagers();
        registerBackCallback();
        startAd();
    }

    // --- AdUIManager.Listener ---

    @Override
    public void onVideoTouched() {
        if (popup != null) popup.handleVideoTap();
    }

    private void lockOrientationToCurrentRotation() {
        int rotation;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Display display = getDisplay();
            rotation = (display != null) ? display.getRotation() : Surface.ROTATION_90;
        } else {
            //noinspection deprecation
            rotation = getWindowManager().getDefaultDisplay().getRotation();
        }

        switch (rotation) {
            case Surface.ROTATION_90:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            case Surface.ROTATION_270:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
                break;
            case Surface.ROTATION_180:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT);
                break;
            default: // ROTATION_0
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
        }

        Log.d(TAG, "Orientation locked to rotation=" + rotation);
    }

    private void setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Must be set before setContentView so the first layout pass extends behind system bars
            getWindow().setDecorFitsSystemWindows(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private void parseIntentConfig() {
        config = new AdConfig(
                getIntent().getStringExtra("VIDEO_PATH"),
                getIntent().getStringExtra("CLICK_URL"),
                getIntent().getBooleanExtra("IS_REWARDED", false),
                getIntent().getIntExtra("CLOSE_BUTTON_DELAY", 5),
                getIntent().getStringExtra("ICON_PATH"),
                getIntent().getIntExtra("POPUP_PEEK_DELAY", 5)
        );
    }

    private void initializeManagers() {
        uiManager = new AdUIManager(this, this, config.isRewarded);
        uiManager.setupUI();
        uiManager.setupFullscreen();
        audioManager = new AdAudioManager(this);
        audioManager.setFocusChangeListener(new AdAudioManager.FocusChangeListener()
        {
            @Override
            public void onAudioFocusPause()
            {
                if (videoPlayer != null && timerManager != null)
                {
                    pausedByAudioFocus = true;
                    videoPlayer.pause();
                    timerManager.pause();
                }
            }
            @Override
            public void onAudioFocusResume()
            {
                if (pausedByAudioFocus && !isFinishing() && videoPlayer != null && timerManager != null)
                {
                    pausedByAudioFocus = false;
                    videoPlayer.resume();
                    timerManager.resume();
                }
            }
        });
        audioManager.requestFocus();
        videoPlayer = new AdVideoPlayer(uiManager.getVideoView(), this);
        timerManager = new AdTimerManager(this, config.closeButtonDelay, config.isRewarded);

        popup = new AdPopup(this, uiManager.getRootLayout(), new AdPopup.Listener()
        {
            @Override
            public void onPeeked() {}

            @Override
            public void onDismissed()
            {
                boolean success = config.isRewarded ? isFullyWatched : true;
                finishWithResult(success);
            }

            @Override
            public void onExpanded()
            {
                if (videoPlayer != null) videoPlayer.pause();
                if (timerManager != null) timerManager.pause();
                uiManager.hideCloseButton();
            }

            @Override
            public void onCollapsed()
            {
                if (!isFinishing() && videoPlayer != null) videoPlayer.resume();
                if (!isFinishing() && timerManager != null) timerManager.resume();
                if (closeButtonEarned) uiManager.showCloseButton();
            }

            @Override
            public void onAdClicked()
            {
                if (callback != null) callback.onAdClicked();
            }
        });
        popup.attach(config);
    }

    private void notifyAdStarted() {
        if (callback != null) callback.onAdStarted();
    }

    private void startAd() {
        videoPlayer.load(config.videoPath, true);
        prepareWatchdog.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS);
    }

    private void finishWithResult(boolean success) {
        if (resultSent) return;
        resultSent = true;
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        unregisterBackCallback();
        if (popup != null) { popup.cancel(); popup = null; }
        if (timerManager != null) timerManager.stop();
        if (audioManager != null) audioManager.release();
        if (callback != null) callback.onAdFinished(success);
        callback = null;
        finish();
    }

    @Override public void onCloseClicked() {
        // Interstitial: always success (ad was displayed)
        // Rewarded: success only if video was fully watched
        boolean success = config.isRewarded ? isFullyWatched : true;
        finishWithResult(success);
    }
    @Override public void onMuteClicked() { audioManager.toggleMute(); uiManager.updateMuteButton(audioManager.isMuted()); }
    @Override public void onVideoPrepared(MediaPlayer mp) {
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        notifyAdStarted(); // Fire only after MediaPlayer confirms the file is valid and playback begins
        audioManager.setMediaPlayer(mp);
        timerManager.start();
        popup.schedulePeek(config.peekDelay);
    }

    @Override public void onVideoCompleted() {
        if (config.isRewarded) {
            isFullyWatched = true;
            uiManager.showRewardEarned();
            uiManager.showCloseButton();
        }
    }

    @Override public void onVideoError(int what, int extra) {
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        String errorMsg = "Video playback error: what=" + what + ", extra=" + extra;
        Log.e(TAG, errorMsg);
        if (callback != null) callback.onAdFailed(errorMsg);
        finishWithResult(false);
    }
    @Override public void onCountdownTick(int rem) {
        uiManager.updateCountdown(rem);
        if (config.isRewarded && !isFullyWatched) {
            // Use max(current, lastPaused) so the timer holds its correct pre-pause value
            // during the keyframe catch-up window after resume, instead of jumping forward.
            int pos = Math.max(videoPlayer.getCurrentPosition(), videoPlayer.getLastPausedPosition());
            timerManager.updateRewardTimer(pos, videoPlayer.getDuration());
        }
    }

    @Override public void onCountdownComplete()
    {
        closeButtonEarned = true;
        if (popup == null || !popup.isExpanded()) uiManager.showCloseButton();
    }
    @Override public void onRewardTimerTick(int rem) { uiManager.updateRewardTimer(rem); }

    private void handleBackNavigation() {
        // Let the popup consume back first if it is expanded
        if (popup != null && popup.handleBackPress()) {
            return;
        }
        if (uiManager != null && uiManager.isCloseButtonVisible()) {
            boolean success = config.isRewarded ? isFullyWatched : true;
            finishWithResult(success);
        }
        // Close button not visible: consume silently — ad stays locked open
    }

    private void registerBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback = this::handleBackNavigation;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    backCallback
            );
        }
    }

    private void unregisterBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            backCallback = null;
        }
    }

    // Android 12 and below — onKeyDown handles KEYCODE_BACK
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && keyCode == KeyEvent.KEYCODE_BACK) {
            handleBackNavigation();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoPlayer != null) videoPlayer.pause();
        if (timerManager != null) timerManager.pause();
        if (popup != null) popup.pauseWebContent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pausedByAudioFocus = false; // lifecycle resume takes precedence; prevents double resume from focus callback
        if (uiManager != null) {
            uiManager.setupFullscreen();
            uiManager.applyInsets();
        }
        // Do not resume video/timer if the popup is expanded — it paused them intentionally.
        // But do resume the WebView so its JS and network activity continue.
        boolean popupExpanded = popup != null && popup.isExpanded();
        if (popupExpanded) {
            popup.resumeWebContent();
        } else if (!isFinishing() && videoPlayer != null && timerManager != null) {
            videoPlayer.resume();
            timerManager.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        if (!resultSent && callback != null) { callback.onAdFinished(false); callback = null; }
        if (currentInstanceRef != null && currentInstanceRef.get() == this) { currentInstanceRef.clear(); currentInstanceRef = null; }
        if (popup != null) { popup.cancel(); popup = null; }
        if (timerManager != null) timerManager.stop();
        if (audioManager != null) audioManager.release();
        if (videoPlayer != null) videoPlayer.stop();
    }

    public static void dismissAd() {
        AdActivity instance = currentInstanceRef != null ? currentInstanceRef.get() : null;
        if (instance != null) instance.runOnUiThread(() -> instance.finishWithResult(false));
    }
}
