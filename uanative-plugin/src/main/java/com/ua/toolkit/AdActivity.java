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
import com.ua.toolkit.display.AdPlayableController;
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
    private AdPlayableController playableController;
    private boolean isPlayable = false;
    private boolean isFullyWatched = false;
    private boolean resultSent = false;
    private boolean pausedByAudioFocus = false;
    private boolean closeButtonEarned = false;
    private boolean resumingFromPlayOverlay = false;
    private boolean adStartedFired  = false;
    private boolean adClickFired    = false;
    private boolean adFeedbackGiven = false;
    private int savedVideoPosition = 0;
    private OnBackInvokedCallback backCallback; // API 33+
    private android.content.BroadcastReceiver noisyAudioReceiver; // headphone unplug

    // Flow B
    private boolean hasVisitedStore   = false;
    private boolean transitionPending = false; // guards against double-posting the 1500ms delay
    private final Handler flowBHandler = new Handler(Looper.getMainLooper());

    private final Handler prepareWatchdog = new Handler(Looper.getMainLooper());
    private Runnable prepareTimeoutRunnable;

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
            String errorMsg = "Invalid config - ad file is missing or path is null"
                    + (config.videoPath != null ? ": " + config.videoPath : "");
            failAd(errorMsg);
            return;
        }

        if (savedInstanceState != null)
        {
            if (callback == null)
            {
                // Process was killed by the OS while the store was open — Unity receiver is gone.
                // Finish cleanly rather than leaving a zombie activity with no callback target.
                Log.w(TAG, "onCreate: process death detected (callback=null) — finishing gracefully");
                finish();
                return;
            }
            // Activity recreated but process survived — restore reward-critical state.
            isFullyWatched    = savedInstanceState.getBoolean("isFullyWatched", false);
            closeButtonEarned = savedInstanceState.getBoolean("closeButtonEarned", false);
            savedVideoPosition = savedInstanceState.getInt("videoPosition", 0);
            hasVisitedStore   = savedInstanceState.getBoolean("hasVisitedStore", false);
            adStartedFired    = savedInstanceState.getBoolean("adStartedFired",  false);
            adClickFired      = savedInstanceState.getBoolean("adClickFired",    false);
            adFeedbackGiven   = savedInstanceState.getBoolean("adFeedbackGiven", false);
            Log.d(TAG, "onCreate: state restored — isFullyWatched=" + isFullyWatched
                    + " closeButtonEarned=" + closeButtonEarned
                    + " videoPosition=" + savedVideoPosition
                    + " adFeedbackGiven=" + adFeedbackGiven);
        }

        initializeManagers();

        // Restore saved video position (process death / activity recreation path — video only)
        if (!isPlayable && savedVideoPosition > 0)
        {
            videoPlayer.setSavedPosition(savedVideoPosition);
            savedVideoPosition = 0;
        }

        // Reapply earned UI state after manager setup (restoration path)
        if (isFullyWatched)         { uiManager.showRewardEarned(); uiManager.showCloseButton(); }
        else if (closeButtonEarned) { uiManager.showCloseButton(); }
        if (config.isFlowB && savedInstanceState != null) {
            boolean engagementMet = isPlayable ? closeButtonEarned : isFullyWatched;
            if (engagementMet && hasVisitedStore) {
                uiManager.setCornerButtonState(AdUIManager.CornerButtonState.CLOSE);
            }
            // If either condition not yet met, OPEN_STORE is already the default — no action needed.
        }

        // Restore per-session single-fire guards on the fresh AdPopup instance
        if (adClickFired)    popup.markClickFired();
        if (adFeedbackGiven) popup.markFeedbackGiven();

        overridePendingTransition(R.anim.slide_in_bottom, 0);
        registerBackCallback();
        startAd();
    }

    // --- AdUIManager.Listener ---

    @Override
    public void onVideoTouched() {
        // Playable: touch passes through to the HTML game — do not open popup on background tap.
        if (!isPlayable && popup != null) popup.handleVideoTap();
    }

    // --- Setup helpers ---

    private void lockOrientationToCurrentRotation() {
        // Game is landscape-only — allow sensor-driven switching between LANDSCAPE and REVERSE_LANDSCAPE
        // so the ad follows 180° device flips without ever snapping to portrait.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
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
        isPlayable = getIntent().getBooleanExtra("IS_PLAYABLE", false);
        config = AdConfig.fromIntent(getIntent());
    }

    private void initUIManager() {
        uiManager = new AdUIManager(this, this, config.isRewarded,
                config.rewardCountdownText, config.rewardEarnedText);
        uiManager.setDisableMuteButton(config.disableMuteButton);
        uiManager.setDisableSkipButton(config.disableSkipButton);
        uiManager.setDisableRewardCountdown(config.disableRewardCountdown);
        uiManager.setRewardTextSizeSp(config.rewardTextSizeSp);
        uiManager.setRewardTextColor(config.rewardTextColor);
        uiManager.setFlowB(config.isFlowB);
        uiManager.setPlayable(isPlayable);
        uiManager.setOpenStoreButtonText(config.openStoreButtonText);
        uiManager.setupUI();
        uiManager.setupFullscreen();
    }

    private void initializeManagers() {
        initUIManager();

        audioManager = new AdAudioManager(this);
        audioManager.setFocusChangeListener(new AdAudioManager.FocusChangeListener()
        {
            @Override
            public void onAudioFocusPause()
            {
                pausedByAudioFocus = true;
                pauseContent();
                if (timerManager != null) timerManager.pause();
            }
            @Override
            public void onAudioFocusResume()
            {
                if (pausedByAudioFocus && !isFinishing() && timerManager != null
                        && (popup == null || !popup.isExpanded()))
                {
                    pausedByAudioFocus = false;
                    resumeContent();
                    timerManager.resume();
                }
            }
        });
        audioManager.requestFocus();

        if (isPlayable) {
            // Remove the VideoView placeholder UIManager added; WebView takes its place.
            android.view.View videoViewPlaceholder = uiManager.getVideoView();
            if (videoViewPlaceholder != null && videoViewPlaceholder.getParent() != null) {
                ((android.view.ViewGroup) videoViewPlaceholder.getParent()).removeView(videoViewPlaceholder);
            }
            try {
                playableController = new AdPlayableController(
                        this, uiManager.getRootLayout(), new AdJsBridge(this),
                        this::onContentReady, this::failAd);
            } catch (Exception e) {
                failAd("WebView initialization failed: " + e.getMessage());
                return;
            }
        } else {
            videoPlayer = new AdVideoPlayer(uiManager.getVideoView(), this);
        }

        // Timer uses elapsed-time countdown for both interstitial and playable.
        // For rewarded playable, reward is earned when closeButtonDelay elapses (engagement gate);
        // no countdown UI is shown for playables to avoid overlapping the HTML game content.
        timerManager = new AdTimerManager(this, config.closeButtonDelay, config.isRewarded, isPlayable);

        popup = new AdPopup(this, uiManager.getRootLayout(), new PopupEventHandler());
        popup.attach(config);

        // Once insets are known, push them to AdPopup so cards sit above the navigation bar.
        // insetsApplied is one-shot so this fires exactly once per activity lifecycle.
        uiManager.setInsetsReadyCallback((bottomInset, rightInset) -> {
            if (popup != null) popup.applyInsets(bottomInset, rightInset);
        });
    }

    // --- Content helpers ---

    /** Pauses HTML game or video (non-lifecycle: audio focus, popup expand, noisy receiver). */
    private void pauseContent() {
        if (isPlayable) {
            if (playableController != null) playableController.pause();
        } else if (videoPlayer != null) {
            videoPlayer.pause();
        }
    }

    /** Resumes HTML game or video. */
    private void resumeContent() {
        if (isPlayable) {
            if (playableController != null) playableController.resume();
        } else if (videoPlayer != null) {
            videoPlayer.resume();
        }
    }

    /** True if the ad outcome counts as a successful engagement (reward granted for rewarded ads). */
    private boolean resolveSuccess() {
        boolean engagementMet = isPlayable ? closeButtonEarned : isFullyWatched;
        return config.isRewarded ? engagementMet : true;
    }

    // --- Popup event handler ---

    private class PopupEventHandler implements AdPopup.Listener
    {
        @Override public void onPeeked() { }

        @Override
        public void onDismissed()
        {
            boolean success = resolveSuccess();
            Log.d(TAG, "popup.onDismissed — success=" + success + " isPlayable=" + isPlayable
                    + " closeButtonEarned=" + closeButtonEarned + " isFullyWatched=" + isFullyWatched);
            finishWithResult(success);
        }

        @Override
        public void onExpanded()
        {
            Log.d(TAG, "popup.onExpanded — pausing video and timer, hiding close button");
            pauseContent();
            if (timerManager != null) timerManager.pause();
            if (!config.isFlowB) uiManager.hideCloseButton(); // Flow B: corner button stays visible (covered by store sheet)
        }

        @Override
        public void onCollapsed()
        {
            hasVisitedStore = true;
            if (config.isFlowB) evaluateFlowBState();
            Log.d(TAG, "popup.onCollapsed — resumingFromPlayOverlay=" + resumingFromPlayOverlay
                    + " closeButtonEarned=" + closeButtonEarned);
            if (!resumingFromPlayOverlay)
            {
                // StoreOpener fallback — resume preemptively in case activity lifecycle
                // doesn't fire (e.g. store fails to open silently). onResume will also
                // resume on normal return, which is a harmless no-op on an already-playing video.
                if (!isFinishing()) resumeContent();
                if (!isFinishing() && timerManager != null) timerManager.resume();
            }
            if (closeButtonEarned || isFullyWatched) uiManager.showCloseButton();
        }

        @Override
        public void onAdClicked()
        {
            if (adClickFired) return;
            adClickFired = true;
            Log.d(TAG, "popup.onAdClicked — firing callback");
            if (callback != null) callback.onAdClicked();
        }

        @Override
        public void onNotInterested()
        {
            adFeedbackGiven = true;
            Log.d(TAG, "popup.onNotInterested — firing callback");
            if (callback != null) callback.onAdFeedback("not_interested");
        }
    }

    // --- Ad lifecycle ---

    private void notifyAdStarted() {
        if (adStartedFired) return;
        adStartedFired = true;
        if (callback != null) callback.onAdStarted();
    }

    private void onContentReady() {
        // Cancel the load watchdog — content is ready regardless of whether this was a video or playable.
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        if (isPlayable) {
            uiManager.showPlayableControls();
            // Re-apply mute state in case the user toggled before onPageFinished fired.
            if (playableController != null) playableController.applyInitialMute(audioManager.isMuted());
        }
        notifyAdStarted();
        timerManager.start();
        popup.schedulePeek(config.peekDelay);
    }

    // Called by AdJsBridge.openStore() from JS — routes to the same store path as native taps.
    void handleStoreRedirect() {
        if (popup != null) popup.openStore();
    }

    private void startAd() {
        if (resultSent) return; // initializeManagers() failed and already called failAd
        prepareTimeoutRunnable = () -> {
            String msg = isPlayable
                ? "Playable ad load timed out — WebView did not fire onPageFinished"
                : "Video prepare timed out — MediaPlayer fired neither onPrepared nor onError";
            failAd(msg);
        };
        if (isPlayable) {
            if (playableController == null) return; // defensive: WebView init failed silently
            try {
                playableController.load(config.videoPath);
            } catch (Exception e) {
                failAd("Playable HTML read/patch failed: " + e.getMessage());
                return;
            }
        } else {
            videoPlayer.load(config.videoPath);
        }
        prepareWatchdog.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS);
    }

    private void finishWithResult(boolean success) {
        Log.d(TAG, "finishWithResult: success=" + success + " resultSent=" + resultSent);
        if (resultSent) return;
        resultSent = true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        flowBHandler.removeCallbacksAndMessages(null);
        unregisterBackCallback();
        if (popup != null) { popup.cancel(); popup = null; }
        if (timerManager != null) timerManager.stop();
        if (audioManager != null) audioManager.release();
        // Destroy the WebView renderer BEFORE firing the callback so that Chromium's implicit
        // audio focus (held by the HTML5 game's Web Audio / Howler) is fully released before
        // the next ad SDK requests focus.
        if (playableController != null) { playableController.destroy(); playableController = null; }
        if (callback != null) callback.onAdFinished(success);
        callback = null;
        finish();
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }

    private void failAd(String reason) {
        Log.e(TAG, "failAd: " + reason);
        if (resultSent) return;
        resultSent = true;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        flowBHandler.removeCallbacksAndMessages(null);
        unregisterBackCallback();
        if (popup != null) { popup.cancel(); popup = null; }
        if (timerManager != null) timerManager.stop();
        if (audioManager != null) audioManager.release();
        if (playableController != null) { playableController.destroy(); playableController = null; }
        if (callback != null) callback.onAdFailed(reason);
        callback = null;
        finish();
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }

    // --- AdUIManager.Listener (continued) ---

    @Override public void onCloseClicked() {
        if (config.isFlowB) {
            handleFlowBCornerButtonTap();
            return;
        }
        boolean success = resolveSuccess();
        Log.d(TAG, "onCloseClicked — isRewarded=" + config.isRewarded + " isPlayable=" + isPlayable
                + " closeButtonEarned=" + closeButtonEarned + " isFullyWatched=" + isFullyWatched + " success=" + success);
        finishWithResult(success);
    }

    private void handleFlowBCornerButtonTap() {
        AdUIManager.CornerButtonState state = uiManager.getCornerButtonState();
        if (state == AdUIManager.CornerButtonState.OPEN_STORE) {
            if (popup != null) popup.openStore();
        } else if (state == AdUIManager.CornerButtonState.CLOSE) {
            finishWithResult(true); // reward always granted in Flow B close
        }
    }

    @Override public void onMuteClicked() {
        audioManager.toggleMute();
        boolean muted = audioManager.isMuted();
        if (isPlayable && playableController != null) playableController.applyMute(muted);
        uiManager.updateMuteButton(muted);
    }

    // --- AdVideoPlayer.Listener ---

    @Override public void onVideoPrepared(MediaPlayer mp) {
        boolean firstPrepare = !adStartedFired;
        Log.d(TAG, "onVideoPrepared — firstPrepare=" + firstPrepare + " peekDelay=" + config.peekDelay);
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        audioManager.setMediaPlayer(mp);
        if (firstPrepare) {
            onContentReady();
        }
    }

    @Override public void onVideoCompleted() {
        Log.d(TAG, "onVideoCompleted — isRewarded=" + config.isRewarded + " isFullyWatched=" + isFullyWatched);
        if (config.isRewarded && !isFullyWatched) {
            isFullyWatched = true;
            timerManager.markRewardEarned();
            uiManager.showRewardEarned();
            uiManager.showCloseButton(); // no-op in Flow B (guarded)
        }
        if (config.isFlowB) evaluateFlowBState();
    }

    @Override public void onVideoError(int what, int extra) {
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        failAd("Video playback error: what=" + what + ", extra=" + extra);
    }

    // --- AdTimerManager.Listener ---

    @Override public void onCountdownTick(int rem) {
        if (!config.isRewarded && !closeButtonEarned && (config.closeButtonDelay - rem) >= config.skipButtonDelaySec) {
            uiManager.showSkipButton();
        }
        if (config.isRewarded && !isFullyWatched && !isPlayable) {
            int pos = Math.max(videoPlayer.getCurrentPosition(), videoPlayer.getLastPausedPosition());
            timerManager.updateRewardTimer(pos, videoPlayer.getDuration());
        }
    }

    @Override public void onCountdownComplete()
    {
        Log.d(TAG, "onCountdownComplete — close button earned, popupExpanded=" + (popup != null && popup.isExpanded()));
        closeButtonEarned = true;
        if (config.isFlowB) {
            // For playable Flow B the timer drives closeButtonEarned (engagement gate).
            // evaluateFlowBState() must be called here so the OPEN STORE → ✕ transition fires
            // if the store was already visited before the timer elapsed.
            // (Video Flow B never reaches this path — rewarded video timer never fires onCountdownComplete.)
            evaluateFlowBState();
            return;
        }
        if (popup == null || !popup.isExpanded()) {
            // If skip button is already visible the user controls the transition by tapping it.
            // Only auto-show close when skip never appeared (disabled or skipDelay >= closeDelay).
            if (!uiManager.isSkipButtonVisible()) uiManager.showCloseButton();
        }
    }

    @Override public void onRewardTimerTick(int rem) { uiManager.updateRewardTimer(rem); }

    // --- Flow B ---

    private void evaluateFlowBState() {
        if (!config.isFlowB) return;
        // Video: engagement = video fully watched. Playable: engagement = timer elapsed (closeButtonEarned).
        boolean engagementMet = isPlayable ? closeButtonEarned : isFullyWatched;
        if (engagementMet && hasVisitedStore) {
            if (!transitionPending) {
                transitionPending = true;
                flowBHandler.postDelayed(() -> {
                    transitionPending = false;
                    uiManager.transitionCornerButtonToClose(300); // flowBFadeDurationMs
                }, 1500); // flowBTransitionDelayMs
            }
        }
        // Either flag not yet true: stays OPEN_STORE — no change needed
    }

    // --- Back navigation ---

    private void handleBackNavigation() {
        if (config.isFlowB) return; // back is always blocked in Flow B
        boolean closeVisible = uiManager != null && uiManager.isCloseButtonVisible();
        if (popup != null && popup.handleBackPress()) {
            return;
        }
        if (closeVisible) {
            finishWithResult(resolveSuccess());
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

    // --- Activity lifecycle ---

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
        if (noisyAudioReceiver != null) {
            unregisterReceiver(noisyAudioReceiver);
            noisyAudioReceiver = null;
        }
        Log.d(TAG, "onPause — suspending video and pausing timer");
        if (isPlayable) {
            if (playableController != null) playableController.pause();
        } else if (videoPlayer != null) {
            videoPlayer.suspend(); // suspend (not pause) so MediaPlayer releases the decoder surface
        }
        if (timerManager != null) timerManager.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean popupExpanded = popup != null && popup.isExpanded();
        Log.d(TAG, "onResume — popupExpanded=" + popupExpanded + " isFinishing=" + isFinishing()
                + " resumingFromPlayOverlay=" + resumingFromPlayOverlay);
        pausedByAudioFocus = false;
        if (uiManager != null) {
            uiManager.setupFullscreen();
            // Re-assert close button — it may have been hidden by onExpanded() before the store opened
            if ((isFullyWatched || closeButtonEarned) && (popup == null || !popup.isExpanded()))
                uiManager.showCloseButton();
        }
        // Do not resume video/timer while the Play Store overlay is still active —
        // onPlayOverlayResult() handles resume when the overlay is dismissed.
        if (!popupExpanded && !isFinishing() && timerManager != null) {
            resumingFromPlayOverlay = false;
            resumeContent();
            timerManager.resume();
        }

        // Register AUDIO_BECOMING_NOISY receiver — active only while in foreground.
        // Headphone unplug fires this broadcast rather than an audio focus event.
        noisyAudioReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context ctx, android.content.Intent intent) {
                if (!android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) return;
                Log.d(TAG, "onBecomingNoisy — audio output changed, pausing ad audio");
                pausedByAudioFocus = true;
                pauseContent();
                if (timerManager != null) timerManager.pause();
            }
        };
        registerReceiver(noisyAudioReceiver,
            new android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isFullyWatched", isFullyWatched);
        outState.putBoolean("closeButtonEarned", closeButtonEarned);
        outState.putBoolean("hasVisitedStore", hasVisitedStore);
        outState.putBoolean("adStartedFired",  adStartedFired);
        outState.putBoolean("adClickFired",    adClickFired);
        outState.putBoolean("adFeedbackGiven", adFeedbackGiven);
        int pos = videoPlayer != null
                ? Math.max(videoPlayer.getCurrentPosition(), videoPlayer.getLastPausedPosition())
                : 0;
        outState.putInt("videoPosition", pos);
        Log.d(TAG, "onSaveInstanceState — isFullyWatched=" + isFullyWatched
                + " closeButtonEarned=" + closeButtonEarned + " videoPosition=" + pos);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy — resultSent=" + resultSent);
        prepareWatchdog.removeCallbacks(prepareTimeoutRunnable);
        flowBHandler.removeCallbacksAndMessages(null);
        if (!resultSent && callback != null) { callback.onAdFinished(false); callback = null; }
        if (currentInstanceRef != null && currentInstanceRef.get() == this) { currentInstanceRef.clear(); currentInstanceRef = null; }
        if (popup != null) { popup.cancel(); popup = null; }
        if (timerManager != null) timerManager.stop();
        if (audioManager != null) audioManager.release();
        if (playableController != null) { playableController.destroy(); playableController = null; }
        if (videoPlayer != null) videoPlayer.stop();
    }

    public static void dismissAd() {
        AdActivity instance = currentInstanceRef != null ? currentInstanceRef.get() : null;
        if (instance != null) instance.runOnUiThread(() -> instance.finishWithResult(false));
    }
}
