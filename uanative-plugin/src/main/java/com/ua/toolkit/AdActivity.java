package com.ua.toolkit;

import android.app.Activity;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import java.lang.ref.WeakReference;

public class AdActivity extends Activity implements
        com.ua.toolkit.AdUIManager.Listener,
        com.ua.toolkit.AdVideoPlayer.Listener,
        AdTimerManager.Listener
{
    private static final String TAG = "AdActivity";
    public static AdCallback callback;
    private static WeakReference<AdActivity> currentInstanceRef;
    private com.ua.toolkit.AdUIManager uiManager;
    private com.ua.toolkit.AdVideoPlayer videoPlayer;
    private com.ua.toolkit.AdAudioManager audioManager;
    private AdTimerManager timerManager;
    private AdConfig config;
    private boolean isFullyWatched = false;
    private boolean resultSent = false;
    private boolean isAdClicked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        currentInstanceRef = new WeakReference<>(this);

        setupWindowFlags();
        parseIntentConfig();

        if (!config.isValid())
        {
            String errorMsg = "Invalid config - video path is null or empty";
            Log.e(TAG, errorMsg);
            if (callback != null) callback.onAdFailed(errorMsg);
            finishWithResult(false);
            return;
        }

        initializeManagers();
        notifyAdStarted();
        startAd();
    }

    // --- AdUIManager.Listener ---

    private HeadlessWebViewResolver currentResolver;

    @Override
    public void onVideoTouched() {
        if (config == null || config.clickUrl == null || config.clickUrl.isEmpty()) {
            return;
        }

        // Ignore subsequent taps - only process the first click
        if (isAdClicked) {
            Log.d(TAG, "Ad already clicked, ignoring tap");
            return;
        }

        isAdClicked = true;

        if (callback != null) {
            callback.onAdClicked();
        }

        Log.d(TAG, "Resolving click URL: " + config.clickUrl);

        currentResolver = new HeadlessWebViewResolver(this);
        currentResolver.setTimeout(15000); // 15 second timeout

        currentResolver.resolve(config.clickUrl, new HeadlessWebViewResolver.ResolverCallback() {
            @Override
            public void onStoreFound(HeadlessWebViewResolver.StoreInfo storeInfo) {
                currentResolver = null;

                StoreOpener.OpenResult result = StoreOpener.openStore(AdActivity.this, storeInfo);

                if (result.success) {
                    Log.d(TAG, "Store opened successfully via " + result.method);

                    if (config.isRewarded && !isFullyWatched) {
                        Log.d(TAG, "Rewarded ad click: Keeping ad open to finish watch time.");
                    } else {
                        // For Interstitials or already finished Rewarded ads, close now
                        finishWithResult(true);
                    }
                } else {
                    handleFailure("Store open failed: " + result.message);
                }
            }

            @Override
            public void onFailed(String reason) {
                Log.w(TAG, "URL resolution failed: " + reason);
                currentResolver = null;
                handleFailure(reason);
            }
        });
    }

    private void handleFailure(String reason) {
        Log.e(TAG, "Ad Click Failed: " + reason);
        if (callback != null) {
            callback.onAdFailed(reason);
        }
    }

    private void setupWindowFlags() {
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
                getIntent().getIntExtra("CLOSE_BUTTON_DELAY", 5)
        );
    }

    private void initializeManagers() {
        uiManager = new com.ua.toolkit.AdUIManager(this, this, config.isRewarded);
        uiManager.setupUI();
        uiManager.setupFullscreen();
        audioManager = new com.ua.toolkit.AdAudioManager(this);
        audioManager.requestFocus();
        videoPlayer = new com.ua.toolkit.AdVideoPlayer(uiManager.getVideoView(), this);
        timerManager = new AdTimerManager(this, config.closeButtonDelay, config.isRewarded);
    }

    private void notifyAdStarted() {
        if (callback != null) callback.onAdStarted();
    }

    private void startAd() {
        videoPlayer.load(config.videoPath, true);
    }

    private void finishWithResult(boolean success) {
        if (resultSent) return;
        resultSent = true;
        timerManager.stop();
        audioManager.release();
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
    @Override public void onVideoPrepared(MediaPlayer mp) { audioManager.setMediaPlayer(mp); timerManager.start(); }

    @Override public void onVideoCompleted() {
        if (config.isRewarded) {
            isFullyWatched = true;
            uiManager.showRewardEarned();
            uiManager.showCloseButton();
        }
    }

    @Override public void onVideoError(int what, int extra) {
        String errorMsg = "Video playback error: what=" + what + ", extra=" + extra;
        Log.e(TAG, errorMsg);
        if (callback != null) callback.onAdFailed(errorMsg);
        finishWithResult(false);
    }
    @Override public void onCountdownTick(int rem) {
        uiManager.updateCountdown(rem);
        if (config.isRewarded && !isFullyWatched) {
            timerManager.updateRewardTimer(videoPlayer.getCurrentPosition(), videoPlayer.getDuration());
        }
    }
    @Override public void onCountdownComplete() { uiManager.showCloseButton(); }
    @Override public void onRewardTimerTick(int rem) { uiManager.updateRewardTimer(rem); }
    @Override public void onBackPressed() {
        if (uiManager.isCloseButtonVisible()) {
            // Same logic as onCloseClicked
            boolean success = config.isRewarded ? isFullyWatched : true;
            finishWithResult(success);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (videoPlayer != null) videoPlayer.pause();
        if (timerManager != null) timerManager.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (uiManager != null) {
            uiManager.setupFullscreen();
            uiManager.applyInsets();
        }
        if (!isFinishing() && videoPlayer != null && timerManager != null) {
            videoPlayer.resume();
            timerManager.resume();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!resultSent && callback != null) { callback.onAdFinished(false); callback = null; }
        if (currentInstanceRef != null && currentInstanceRef.get() == this) { currentInstanceRef.clear(); currentInstanceRef = null; }
        if (currentResolver != null) { currentResolver.cancel(); currentResolver = null; }
        if (timerManager != null) timerManager.stop();
        if (audioManager != null) audioManager.release();
        if (videoPlayer != null) videoPlayer.stop();
    }

    public static void dismissAd() {
        AdActivity instance = currentInstanceRef != null ? currentInstanceRef.get() : null;
        if (instance != null) instance.runOnUiThread(() -> instance.finishWithResult(false));
    }
}