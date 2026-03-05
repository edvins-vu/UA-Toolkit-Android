package com.ua.toolkit;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
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
    private boolean resumingFromPlayOverlay = false;
    private boolean adStartedFired = false;
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
        Log.d(TAG, "onCreate");

        lockOrientationToCurrentRotation();
        setupWindowFlags();
        parseIntentConfig();
        Log.d(TAG, "config: videoPath=" + config.videoPath
                + " bundleId=" + config.bundleId
                + " appName=" + config.appName
                + " clickUrl=" + config.clickUrl
                + " isRewarded=" + config.isRewarded
                + " closeButtonDelay=" + config.closeButtonDelay
                + " peekDelay=" + config.peekDelay);

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
        overridePendingTransition(R.anim.slide_in_bottom, 0);
        registerBackCallback();
        startAd();
    }

    // --- AdUIManager.Listener ---

    @Override
    public void onVideoTouched() {
        if (popup != null) popup.handleVideoTap();
    }

    private void lockOrientationToCurrentRotation() {
        // Game is landscape-only — allow sensor-driven switching between LANDSCAPE and REVERSE_LANDSCAPE
        // so the ad follows 180° device flips without ever snapping to portrait.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        Log.d(TAG, "Orientation locked to SENSOR_LANDSCAPE");
    }

    private void setupWindowFlags() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
                getIntent().getIntExtra("POPUP_PEEK_DELAY", 5),
                getIntent().getStringExtra("BUNDLE_ID"),
                getIntent().getStringExtra("APP_NAME"),
                getIntent().getStringExtra("GET_BUTTON_TEXT"),
                getIntent().getStringExtra("REWARD_COUNTDOWN_TEXT"),
                getIntent().getStringExtra("REWARD_EARNED_TEXT")
        );
    }

    private void initializeManagers() {
        uiManager = new AdUIManager(this, this, config.isRewarded,
                config.rewardCountdownText, config.rewardEarnedText);
        uiManager.setupUI();
        uiManager.setupFullscreen();
        audioManager = new AdAudioManager(this);
        audioManager.setFocusChangeListener(new AdAudioManager.FocusChangeListener()
        {
            @Override
            public void onAudioFocusPause()
            {
                Log.d(TAG, "onAudioFocusPause — pausing video and timer");
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
                Log.d(TAG, "onAudioFocusResume — pausedByAudioFocus=" + pausedByAudioFocus);
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
            public void onPeeked() {
                Log.d(TAG, "popup.onPeeked — Stage 1 visible");
            }

            @Override
            public void onDismissed()
            {
                boolean success = config.isRewarded ? isFullyWatched : true;
                Log.d(TAG, "popup.onDismissed — success=" + success);
                finishWithResult(success);
            }

            @Override
            public void onExpanded()
            {
                Log.d(TAG, "popup.onExpanded — pausing video and timer, hiding close button");
                if (videoPlayer != null) videoPlayer.pause();
                if (timerManager != null) timerManager.pause();
                uiManager.hideCloseButton();
            }

            @Override
            public void onCollapsed()
            {
                Log.d(TAG, "popup.onCollapsed — resumingFromPlayOverlay=" + resumingFromPlayOverlay + " closeButtonEarned=" + closeButtonEarned);
                if (!resumingFromPlayOverlay)
                {
                    // StoreOpener fallback path — onResume won't fire, so resume here
                    if (!isFinishing() && videoPlayer != null) videoPlayer.resume();
                    if (!isFinishing() && timerManager != null) timerManager.resume();
                }
                if (closeButtonEarned) uiManager.showCloseButton();
            }

            @Override
            public void onAdClicked()
            {
                Log.d(TAG, "popup.onAdClicked — firing callback");
                if (callback != null) callback.onAdClicked();
            }
        });
        popup.attach(config);
    }

    private void notifyAdStarted() {
        if (adStartedFired) {
            Log.d(TAG, "notifyAdStarted — already fired, skipping duplicate");
            return;
        }
        adStartedFired = true;
        Log.d(TAG, "notifyAdStarted — firing onAdStarted callback");
        if (callback != null) callback.onAdStarted();
    }

    private void startAd() {
        Log.d(TAG, "startAd — loading video: " + config.videoPath);
        videoPlayer.load(config.videoPath, true);
        prepareWatchdog.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS);
    }

    private void finishWithResult(boolean success) {
        Log.d(TAG, "finishWithResult: success=" + success + " resultSent=" + resultSent);
        if (resultSent) return;
        resultSent = true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        unregisterBackCallback();
        if (popup != null) { popup.cancel(); popup = null; }
        if (timerManager != null) timerManager.stop();
        if (audioManager != null) audioManager.release();
        if (callback != null) callback.onAdFinished(success);
        callback = null;
        finish();
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }

    @Override public void onCloseClicked() {
        boolean success = config.isRewarded ? isFullyWatched : true;
        Log.d(TAG, "onCloseClicked — isRewarded=" + config.isRewarded + " isFullyWatched=" + isFullyWatched + " success=" + success);
        finishWithResult(success);
    }
    @Override public void onMuteClicked() {
        audioManager.toggleMute();
        uiManager.updateMuteButton(audioManager.isMuted());
        Log.d(TAG, "onMuteClicked — isMuted=" + audioManager.isMuted());
    }
    @Override public void onVideoPrepared(MediaPlayer mp) {
        boolean firstPrepare = !adStartedFired;
        Log.d(TAG, "onVideoPrepared — firstPrepare=" + firstPrepare + " peekDelay=" + config.peekDelay);
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        notifyAdStarted();
        audioManager.setMediaPlayer(mp);
        if (firstPrepare) {
            timerManager.start();
            popup.schedulePeek(config.peekDelay);
        }
    }

    @Override public void onVideoCompleted() {
        Log.d(TAG, "onVideoCompleted — isRewarded=" + config.isRewarded);
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
            int pos = Math.max(videoPlayer.getCurrentPosition(), videoPlayer.getLastPausedPosition());
            timerManager.updateRewardTimer(pos, videoPlayer.getDuration());
        }
    }

    @Override public void onCountdownComplete()
    {
        Log.d(TAG, "onCountdownComplete — close button earned, popupExpanded=" + (popup != null && popup.isExpanded()));
        closeButtonEarned = true;
        if (popup == null || !popup.isExpanded()) uiManager.showCloseButton();
    }
    @Override public void onRewardTimerTick(int rem) { uiManager.updateRewardTimer(rem); }

    private void handleBackNavigation() {
        boolean closeVisible = uiManager != null && uiManager.isCloseButtonVisible();
        Log.d(TAG, "handleBackNavigation — closeButtonVisible=" + closeVisible);
        if (popup != null && popup.handleBackPress()) {
            return;
        }
        if (closeVisible) {
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + " resultCode=" + resultCode
                + " isPlayOverlay=" + (requestCode == AdPopup.REQUEST_PLAY_OVERLAY));
        if (requestCode == AdPopup.REQUEST_PLAY_OVERLAY && popup != null) {
            resumingFromPlayOverlay = true; // onResume fires after this — let it do the resume
            popup.onPlayOverlayResult();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause — pausing video and timer");
        if (videoPlayer != null) videoPlayer.pause();
        if (timerManager != null) timerManager.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean popupExpanded = popup != null && popup.isExpanded();
        Log.d(TAG, "onResume — popupExpanded=" + popupExpanded + " isFinishing=" + isFinishing() + " resumingFromPlayOverlay=" + resumingFromPlayOverlay);
        pausedByAudioFocus = false;
        if (uiManager != null) {
            uiManager.setupFullscreen();
            uiManager.applyInsets();
        }
        // Do not resume video/timer while the Play Store overlay is still active —
        // onPlayOverlayResult() handles resume when the overlay is dismissed.
        if (!popupExpanded && !isFinishing() && videoPlayer != null && timerManager != null) {
            resumingFromPlayOverlay = false;
            videoPlayer.resume();
            timerManager.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy — resultSent=" + resultSent);
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
