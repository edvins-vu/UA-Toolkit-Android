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
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
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
    private boolean isPlayable = false;
    private boolean pageLoaded = false; // guards WebViewClient.onPageFinished double-fire
    private WebView webView = null;
    private boolean isFullyWatched = false;
    private boolean resultSent = false;
    private boolean pausedByAudioFocus = false;
    private boolean closeButtonEarned = false;
    private boolean resumingFromPlayOverlay = false;
    private boolean adStartedFired = false;
    private int savedVideoPosition = 0;
    private OnBackInvokedCallback backCallback; // API 33+

    // Flow B
    private boolean hasVisitedStore   = false;
    private boolean transitionPending = false; // guards against double-posting the 1500ms delay
    private final Handler flowBHandler = new Handler(Looper.getMainLooper());

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
            String errorMsg = "Invalid config - ad file is missing or path is null"
                    + (config.videoPath != null ? ": " + config.videoPath : "");
            Log.e(TAG, errorMsg);
            if (callback != null) callback.onAdFailed(errorMsg);
            finishWithResult(false);
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
            Log.d(TAG, "onCreate: state restored — isFullyWatched=" + isFullyWatched
                    + " closeButtonEarned=" + closeButtonEarned
                    + " videoPosition=" + savedVideoPosition);
        }

        initializeManagers();

        // Restore saved video position (process death / activity recreation path — video only)
        if (!isPlayable && savedVideoPosition > 0)
        {
            videoPlayer.setSavedPosition(savedVideoPosition);
            savedVideoPosition = 0;
        }

        // Reapply earned UI state after manager setup (restoration path)
        if (isFullyWatched)        { uiManager.showRewardEarned(); uiManager.showCloseButton(); }
        else if (closeButtonEarned) { uiManager.showCloseButton(); }
        if (config.isFlowB && savedInstanceState != null) evaluateFlowBState();

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
        config = new AdConfig(
                // Core
                getIntent().getStringExtra("VIDEO_PATH"),
                getIntent().getStringExtra("CLICK_URL"),
                getIntent().getBooleanExtra("IS_REWARDED", false),
                getIntent().getBooleanExtra("IS_FLOW_B", false),
                getIntent().getStringExtra("BUNDLE_ID"),

                // Timing
                getIntent().getIntExtra("CLOSE_BUTTON_DELAY", -1),
                getIntent().getIntExtra("POPUP_PEEK_DELAY", -1),
                getIntent().getIntExtra("SKIP_BUTTON_DELAY", -1),
                getIntent().getIntExtra("PULSE_START_DELAY", -1),

                // GET button
                getIntent().getStringExtra("GET_BUTTON_TEXT"),
                getIntent().getStringExtra("GET_BUTTON_COLOR"),
                getIntent().getStringExtra("GET_BUTTON_TEXT_COLOR"),
                getIntent().getIntExtra("GET_BUTTON_WIDTH_DP", -1),
                getIntent().getIntExtra("GET_BUTTON_HEIGHT_DP", -1),
                getIntent().getIntExtra("GET_BUTTON_TEXT_SIZE_SP", -1),
                getIntent().getIntExtra("GET_BUTTON_CORNER_DP", -1),

                // Popup card
                getIntent().getStringExtra("CARD_BG_COLOR"),
                getIntent().getIntExtra("CARD_CORNER_DP", -1),

                // Controls
                getIntent().getBooleanExtra("DISABLE_MUTE_BUTTON", false),
                getIntent().getBooleanExtra("DISABLE_SKIP_BUTTON", false),
                getIntent().getBooleanExtra("DISABLE_PULSE", false),
                getIntent().getBooleanExtra("DISABLE_POPUP_BACKGROUND", false),

                // Reward texts
                getIntent().getStringExtra("REWARD_COUNTDOWN_TEXT"),
                getIntent().getStringExtra("REWARD_EARNED_TEXT"),
                getIntent().getBooleanExtra("DISABLE_REWARD_COUNTDOWN", false),
                getIntent().getIntExtra("REWARD_TEXT_SIZE_SP", -1),
                getIntent().getStringExtra("REWARD_TEXT_COLOR"),
                getIntent().getStringExtra("OPEN_STORE_BUTTON_TEXT")
        );
    }

    private void initializeManagers() {
        uiManager = new AdUIManager(this, this, config.isRewarded,
                config.rewardCountdownText, config.rewardEarnedText);
        uiManager.setDisableMuteButton(config.disableMuteButton);
        uiManager.setDisableSkipButton(config.disableSkipButton);
        uiManager.setDisableRewardCountdown(config.disableRewardCountdown);
        uiManager.setRewardTextSizeSp(config.rewardTextSizeSp);
        uiManager.setRewardTextColor(config.rewardTextColor);
        uiManager.setFlowB(config.isFlowB);
        uiManager.setOpenStoreButtonText(config.openStoreButtonText);
        uiManager.setupUI();
        uiManager.setupFullscreen();
        audioManager = new AdAudioManager(this);
        audioManager.setFocusChangeListener(new AdAudioManager.FocusChangeListener()
        {
            @Override
            public void onAudioFocusPause()
            {
                pausedByAudioFocus = true;
                if (isPlayable) { if (webView != null) webView.onPause(); }
                else if (videoPlayer != null) { videoPlayer.pause(); }
                if (timerManager != null) timerManager.pause();
            }
            @Override
            public void onAudioFocusResume()
            {
                if (pausedByAudioFocus && !isFinishing() && timerManager != null
                        && (popup == null || !popup.isExpanded()))
                {
                    pausedByAudioFocus = false;
                    if (isPlayable) { if (webView != null) webView.onResume(); }
                    else if (videoPlayer != null) { videoPlayer.resume(); }
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
            webView = new WebView(this);
            WebSettings ws = webView.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setDatabaseEnabled(true);
            ws.setMediaPlaybackRequiresUserGesture(false);
            webView.addJavascriptInterface(new AdJsBridge(this), "AdBridge");
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    if (pageLoaded) return;
                    pageLoaded = true;
                    onContentReady();
                }
                @Override
                public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                    if (!req.isForMainFrame()) return;
                    String msg = "WebView error: " + (err != null ? err.getDescription() : "unknown");
                    Log.e(TAG, msg);
                    if (callback != null) callback.onAdFailed(msg);
                    finishWithResult(false);
                }
            });
            FrameLayout.LayoutParams matchParent = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            uiManager.getRootLayout().addView(webView, 0, matchParent);
            webView.loadUrl(config.videoPath);
        } else {
            videoPlayer = new AdVideoPlayer(uiManager.getVideoView(), this);
        }
        // Playable always uses isRewarded=false: no reward countdown UI, timer elapsed = engagement gate.
        timerManager = new AdTimerManager(this, config.closeButtonDelay,
                isPlayable ? false : config.isRewarded);

        popup = new AdPopup(this, uiManager.getRootLayout(), new AdPopup.Listener()
        {
            @Override
            public void onPeeked() { }

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
                if (isPlayable) { if (webView != null) webView.onPause(); }
                else if (videoPlayer != null) { videoPlayer.pause(); }
                if (timerManager != null) timerManager.pause();
                if (!config.isFlowB) uiManager.hideCloseButton(); // Flow B: corner button stays visible (covered by store sheet)
            }

            @Override
            public void onCollapsed()
            {
                hasVisitedStore = true;
                if (config.isFlowB) evaluateFlowBState();
                Log.d(TAG, "popup.onCollapsed — resumingFromPlayOverlay=" + resumingFromPlayOverlay + " closeButtonEarned=" + closeButtonEarned);
                if (!resumingFromPlayOverlay)
                {
                    // StoreOpener fallback — resume preemptively in case activity lifecycle
                    // doesn't fire (e.g. store fails to open silently). onResume will also
                    // resume on normal return, which is a harmless no-op on an already-playing video.
                    if (!isFinishing()) {
                        if (isPlayable) { if (webView != null) webView.onResume(); }
                        else if (videoPlayer != null) { videoPlayer.resume(); }
                    }
                    if (!isFinishing() && timerManager != null) timerManager.resume();
                }
                if (closeButtonEarned || isFullyWatched) uiManager.showCloseButton();
            }

            @Override
            public void onAdClicked()
            {
                Log.d(TAG, "popup.onAdClicked — firing callback");
                if (callback != null) callback.onAdClicked();
            }
        });
        popup.attach(config);

        // Once insets are known, push them to AdPopup so cards sit above the navigation bar.
        // insetsApplied is one-shot so this fires exactly once per activity lifecycle.
        uiManager.setInsetsReadyCallback((bottomInset, rightInset) -> {
            if (popup != null) popup.applyInsets(bottomInset, rightInset);
        });
    }

    private void notifyAdStarted() {
        if (adStartedFired) return;
        adStartedFired = true;
        if (callback != null) callback.onAdStarted();
    }

    private void onContentReady() {
        notifyAdStarted();
        timerManager.start();
        popup.schedulePeek(config.peekDelay);
    }

    // Called by AdJsBridge.openStore() from JS — routes to the same store path as native taps.
    void handleStoreRedirect() {
        if (popup != null) popup.openStore();
    }

    private void startAd() {
        if (!isPlayable) {
            videoPlayer.load(config.videoPath);
            prepareWatchdog.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS);
        }
        // Playable: WebView is already loading via webView.loadUrl() in initializeManagers().
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
        if (callback != null) callback.onAdFinished(success);
        callback = null;
        finish();
        overridePendingTransition(0, R.anim.slide_out_bottom);
    }

    @Override public void onCloseClicked() {
        if (config.isFlowB) {
            handleFlowBCornerButtonTap();
            return;
        }
        boolean success = config.isRewarded ? isFullyWatched : true;
        Log.d(TAG, "onCloseClicked — isRewarded=" + config.isRewarded + " isFullyWatched=" + isFullyWatched + " success=" + success);
        finishWithResult(success);
    }

    private void handleFlowBCornerButtonTap() {
        AdUIManager.CornerButtonState state = uiManager.getCornerButtonState();
        if (state == AdUIManager.CornerButtonState.OPEN_STORE) {
            if (popup != null) popup.openStore();
        } else if (state == AdUIManager.CornerButtonState.CLOSE) {
            finishWithResult(true); // reward always granted in Flow B close
        }
        // FINISH_VIDEO state: button is disabled — tap should not reach here
    }
    @Override public void onMuteClicked() {
        audioManager.toggleMute();
        boolean muted = audioManager.isMuted();
        if (isPlayable && webView != null) {
            webView.evaluateJavascript(
                "(function(){ document.querySelectorAll('audio,video')" +
                ".forEach(function(el){ el.muted=" + muted + "; }); })()", null);
        }
        uiManager.updateMuteButton(muted);
    }
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
        String errorMsg = "Video playback error: what=" + what + ", extra=" + extra;
        Log.e(TAG, errorMsg);
        if (callback != null) callback.onAdFailed(errorMsg);
        finishWithResult(false);
    }
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
        if (config.isFlowB) return; // Flow B ignores countdown-based close
        if (popup == null || !popup.isExpanded()) {
            // If skip button is already visible the user controls the transition by tapping it.
            // Only auto-show close when skip never appeared (disabled or skipDelay >= closeDelay).
            if (!uiManager.isSkipButtonVisible()) uiManager.showCloseButton();
        }
    }
    @Override public void onRewardTimerTick(int rem) { uiManager.updateRewardTimer(rem); }

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

    private void handleBackNavigation() {
        if (config.isFlowB) return; // back is always blocked in Flow B
        boolean closeVisible = uiManager != null && uiManager.isCloseButtonVisible();
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
        Log.d(TAG, "onPause — suspending video and pausing timer");
        if (isPlayable) { if (webView != null) webView.onPause(); }
        else if (videoPlayer != null) { videoPlayer.suspend(); }
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
            // Re-assert close button — it may have been hidden by onExpanded() before the store opened
            if ((isFullyWatched || closeButtonEarned) && (popup == null || !popup.isExpanded()))
                uiManager.showCloseButton();
        }
        // Do not resume video/timer while the Play Store overlay is still active —
        // onPlayOverlayResult() handles resume when the overlay is dismissed.
        if (!popupExpanded && !isFinishing() && timerManager != null) {
            resumingFromPlayOverlay = false;
            if (isPlayable) { if (webView != null) webView.onResume(); }
            else if (videoPlayer != null) { videoPlayer.resume(); }
            timerManager.resume();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isFullyWatched", isFullyWatched);
        outState.putBoolean("closeButtonEarned", closeButtonEarned);
        outState.putBoolean("hasVisitedStore", hasVisitedStore);
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
        if (isPlayable && webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        if (videoPlayer != null) videoPlayer.stop();
    }

    public static void dismissAd() {
        AdActivity instance = currentInstanceRef != null ? currentInstanceRef.get() : null;
        if (instance != null) instance.runOnUiThread(() -> instance.finishWithResult(false));
    }
}
