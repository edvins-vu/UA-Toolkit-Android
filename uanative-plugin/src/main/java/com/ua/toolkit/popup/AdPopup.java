package com.ua.toolkit.popup;

import com.ua.toolkit.AdConfig;
import com.ua.toolkit.UAStoreLauncher;
import com.ua.toolkit.store.StoreOpener;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AdPopup
{
    public interface Listener
    {
        void onPeeked();       // Stage 1 first becomes visible
        void onDismissed();    // Popup fully done — ad should close
        void onExpanded();     // Stage 2 shown — pause video
        void onCollapsed();    // Stage 2 → Stage 3 — resume video
        void onAdClicked();    // Stage 1 GET tapped — fire analytics
    }

    public static final int REQUEST_PLAY_OVERLAY = 1001;

    private static final String TAG = "AdPopup";

    private enum State { HIDDEN, PEEK, PLAY_OVERLAY, COLLAPSED }

    private final Activity _activity;
    private final FrameLayout _rootLayout;
    private final Listener _listener;
    private final Handler _handler = new Handler(Looper.getMainLooper());
    private final AdPopupLayout _layout = new AdPopupLayout();

    private LinearLayout _stage1Card;
    private View _stage3Card;

    private boolean _isCancelled = false;
    private boolean _isAdClicked = false;
    private State _state = State.HIDDEN;

    private String _bundleId;
    private AdConfig _config;

    private TextView _stage1GetButton;
    private TextView _stage3GetButton;
    private Animator _stage1PulseAnimator;
    private Animator _stage3PulseAnimator;
    private Animator _stage3TapPulseAnimator;
    private Runnable _stage1PulseRunnable;
    private Runnable _stage3PulseRunnable;
    private Runnable _scheduledPeekRunnable;
    private boolean _clickUrlFired = false; // ensures Adjust click fires only once per ad session

    // Insets — set once via applyInsets() when AdUIManager receives its first inset dispatch
    private int _cardBottomInset = 0;
    private int _cardRightInset  = 0;
    // Timestamp of the last peek() call — guards against immediate store launch during animation
    private long _peekTimeMs = 0;

    public AdPopup(Activity activity, FrameLayout rootLayout, Listener listener)
    {
        _activity = activity;
        _rootLayout = rootLayout;
        _listener = listener;
    }

    // --- Public API ---

    public void attach(AdConfig config)
    {
        _config = config;
        _bundleId = config.bundleId;
        _layout.updateFromConfig(config);
        _rootLayout.setClipChildren(false);
        buildStage1Card(config);

        // Insert at index 1 (above video surface at 0, below UIManager controls)
        _rootLayout.addView(_stage1Card, 1);
        // Use rootLayout height as the initial off-screen position — guaranteed beyond the
        // visible area on any device, avoiding a one-frame flash at an arbitrary 200dp offset.
        _stage1Card.setTranslationY(_rootLayout.getHeight() > 0
                ? _rootLayout.getHeight() : dpToPx(_layout.offscreenFallbackDp));
        _stage1Card.setVisibility(View.INVISIBLE);
    }

    public void schedulePeek(int delaySeconds)
    {
        _scheduledPeekRunnable = () ->
        {
            if (!_isCancelled && _state == State.HIDDEN) peek();
        };
        _handler.postDelayed(_scheduledPeekRunnable, delaySeconds * 1000L);
    }

    /** Returns true while the Play Store half-sheet overlay is active (video should stay paused). */
    public boolean isExpanded()
    {
        return _state == State.PLAY_OVERLAY;
    }

    public boolean handleBackPress()
    {
        // PLAY_OVERLAY and COLLAPSED: defer to AdActivity close button
        return false;
    }

    /**
     * Programmatically opens the Play Store from any popup state.
     * Tracks the click (once) and fires the appropriate state transition.
     * Used by AdActivity in Flow B when the corner "OPEN STORE" button is tapped.
     */
    public void openStore()
    {
        if (_isCancelled) return;
        if (!_isAdClicked)
        {
            _isAdClicked = true;
            _listener.onAdClicked();
        }
        switch (_state)
        {
            case HIDDEN:
                // Peek first, then open store after slide-in completes
                peek();
                _handler.postDelayed(this::launchPlayOverlay, _layout.slideInDurationMs);
                break;
            case PEEK:
            case COLLAPSED:
                launchPlayOverlay();
                break;
            case PLAY_OVERLAY:
                break; // already open — no-op
        }
    }

    /** Called by AdActivity when the user taps the video surface. */
    public void handleVideoTap()
    {
        if (_isCancelled) return;
        switch (_state)
        {
            case HIDDEN:
                // Cancel only the scheduled auto-peek — leave other handler messages intact
                if (_scheduledPeekRunnable != null)
                {
                    _handler.removeCallbacks(_scheduledPeekRunnable);
                    _scheduledPeekRunnable = null;
                }
                peek();
                break;
            case PEEK:
                // Guard: if the card is still animating in, ignore the tap so the user
                // actually sees the popup before the store opens.
                if (System.currentTimeMillis() - _peekTimeMs < _layout.slideInDurationMs)
                    break;
                // Video tap while Stage 1 visible → same outcome as GET button
                if (!_isAdClicked)
                {
                    _isAdClicked = true;
                    _listener.onAdClicked();
                }
                launchPlayOverlay();
                break;
            case PLAY_OVERLAY:
                break;
            case COLLAPSED:
                // Stage 3 is showing — draw attention to it
                pulsateStage3Card();
                break;
        }
    }

    /**
     * Called by AdActivity.onActivityResult when REQUEST_PLAY_OVERLAY returns.
     * Transitions to COLLAPSED and shows the Stage 3 persistent card.
     */
    public void onPlayOverlayResult()
    {
        if (_state != State.PLAY_OVERLAY) return;
        Log.d(TAG, "state → COLLAPSED (Play overlay dismissed)");
        _state = State.COLLAPSED;
        _listener.onCollapsed();
        showStage3Card();
        // Restart the 5-second pulse countdown each time the user returns from the store
        scheduleStage3Pulse();
    }

    /**
     * Called once by AdActivity after AdUIManager fires its first inset dispatch.
     * Updates both Stage 1 and Stage 3 card margins so they sit above the navigation bar.
     * Stage 3 cards built later via showStage3Card() pick up _cardBottomInset automatically.
     */
    public void applyInsets(int bottomInset, int rightInset)
    {
        _cardBottomInset = bottomInset;
        _cardRightInset  = rightInset;
        int bottom = bottomInset + dpToPx(_layout.cardEdgeMarginDp);
        int right  = rightInset  + dpToPx(_layout.cardEdgeMarginDp);
        if (_stage1Card != null)
        {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) _stage1Card.getLayoutParams();
            lp.bottomMargin = bottom;
            lp.rightMargin  = right;
            _stage1Card.setLayoutParams(lp);
        }
        if (_stage3Card != null)
        {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) _stage3Card.getLayoutParams();
            lp.bottomMargin = bottom;
            lp.rightMargin  = right;
            _stage3Card.setLayoutParams(lp);
        }
    }

    /**
     * Restores click-fired state after activity recreation — prevents double attribution
     * on a fresh AdPopup instance that would otherwise start with _clickUrlFired=false.
     */
    public void markClickFired()
    {
        _clickUrlFired = true;
    }

    public void cancel()
    {
        Log.d(TAG, "cancel: state=" + _state + " stage1=" + (_stage1Card != null) + " stage3=" + (_stage3Card != null));
        _isCancelled = true;
        UAStoreLauncher.cancel(); // stop any in-progress fallback store resolution
        _handler.removeCallbacksAndMessages(null);
        if (_stage1PulseAnimator != null)    { _stage1PulseAnimator.cancel();    _stage1PulseAnimator    = null; }
        if (_stage3PulseAnimator != null)    { _stage3PulseAnimator.cancel();    _stage3PulseAnimator    = null; }
        if (_stage3TapPulseAnimator != null) { _stage3TapPulseAnimator.cancel(); _stage3TapPulseAnimator = null; }
        if (_stage1GetButton != null) { _stage1GetButton.setScaleX(1f); _stage1GetButton.setScaleY(1f); }
        if (_stage3GetButton != null) { _stage3GetButton.setScaleX(1f); _stage3GetButton.setScaleY(1f); }
        _stage1PulseRunnable = null;
        _stage3PulseRunnable = null;
        if (_stage3Card != null)
        {
            _stage3Card.animate().cancel();
            _rootLayout.removeView(_stage3Card);
            _stage3Card = null;
        }
        if (_stage1Card != null)
        {
            _stage1Card.animate().cancel();
            _rootLayout.removeView(_stage1Card);
            _stage1Card = null;
        }
    }

    // --- View Construction ---

    private void buildStage1Card(AdConfig config)
    {
        TextView[] btn = new TextView[1];
        _stage1Card = buildPillCard(() ->
        {
            if (!_isAdClicked)
            {
                _isAdClicked = true;
                _listener.onAdClicked();
            }
            launchPlayOverlay();
        }, btn);
        _stage1GetButton = btn[0];
        // Pulse is scheduled from peek() — when the card is actually visible to the user
    }

    private LinearLayout buildPillCard(Runnable onGetClick, TextView[] buttonOut)
    {
        LinearLayout card = new LinearLayout(_activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        card.setPadding(dpToPx(_layout.cardPaddingHorizontalDp), dpToPx(_layout.cardPaddingVerticalDp),
                dpToPx(_layout.cardPaddingHorizontalDp), dpToPx(_layout.cardPaddingVerticalDp));
        card.setClipChildren(false);
        card.setClipToPadding(false);

        if (_config == null || !_config.disablePopupBackground)
            card.setBackground(AdVisualsHelper.makeCardBackground(
                    _config, dpToPx(_layout.cardCornerRadiusDp)));
        else
            card.setBackground(null);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        cardLp.rightMargin  = _cardRightInset  + dpToPx(_layout.cardEdgeMarginDp);
        cardLp.bottomMargin = _cardBottomInset + dpToPx(_layout.cardEdgeMarginDp);
        card.setLayoutParams(cardLp);

        buttonOut[0] = addGetButton(card, onGetClick);
        return card;
    }

    private TextView addGetButton(LinearLayout parent, Runnable onClick)
    {
        TextView getBtn = new TextView(_activity);
        getBtn.setText(_config != null ? _config.getButtonText : "GET");
        getBtn.setTextColor(AdVisualsHelper.parseButtonTextColor(_config));
        getBtn.setTextSize(_layout.buttonTextSizeSp);
        getBtn.setTypeface(Typeface.DEFAULT_BOLD);
        getBtn.setGravity(Gravity.CENTER);
        getBtn.setBackground(AdVisualsHelper.makeButtonBackground(
                _config, dpToPx(_layout.buttonCornerRadiusDp)));

        int buttonWidth  = dpToPx(_layout.buttonWidthDp);
        int buttonHeight = _layout.buttonHeightDp > 0
                ? dpToPx(_layout.buttonHeightDp)
                : LinearLayout.LayoutParams.WRAP_CONTENT;
        int padV = _layout.buttonHeightDp <= 0 ? dpToPx(_layout.buttonPaddingVerticalDp) : 0;
        getBtn.setPadding(0, padV, 0, padV);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                buttonWidth,
                buttonHeight
        );

        params.gravity = Gravity.CENTER_VERTICAL;
        getBtn.setLayoutParams(params);

        getBtn.setOnClickListener(v ->
        {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onClick.run();
        });

        parent.addView(getBtn);
        return getBtn;
    }

    // --- Stage 2: Play Store Native Sheet overlay

    void launchPlayOverlay()
    {
        if (_state != State.PEEK && _state != State.COLLAPSED) return;

        // Stop pulse animations and snap buttons back to rest scale
        if (_stage1PulseRunnable != null) { _handler.removeCallbacks(_stage1PulseRunnable); _stage1PulseRunnable = null; }
        if (_stage3PulseRunnable != null) { _handler.removeCallbacks(_stage3PulseRunnable); _stage3PulseRunnable = null; }
        if (_stage1PulseAnimator != null) { _stage1PulseAnimator.cancel(); _stage1PulseAnimator = null; }
        if (_stage3PulseAnimator != null) { _stage3PulseAnimator.cancel(); _stage3PulseAnimator = null; }
        if (_stage1GetButton != null) { _stage1GetButton.setScaleX(1f); _stage1GetButton.setScaleY(1f); }
        if (_stage3GetButton != null) { _stage3GetButton.setScaleX(1f); _stage3GetButton.setScaleY(1f); }

        if (_bundleId == null || _bundleId.isEmpty())
        {
            Log.e(TAG, "launchPlayOverlay: bundleId is empty — cannot launch half-sheet");
            return;
        }

        // Extract Adjust token from clickUrl and embed as referrer for attribution.
        // Google Play's Install Referrer API delivers it to the installed app on first launch.
        // Guard: if token extraction fails (null clickUrl, no path segment, parse error) omit the
        // tracker param rather than forwarding the literal string "adjust_tracker=null" to Adjust.
        String tracker = extractAdjustToken(_config != null ? _config.clickUrl : null);
        String rawReferrer = tracker != null
                ? "adjust_tracker=" + tracker + "&utm_source=adjust_store"
                : "utm_source=adjust_store";
        // Encode the entire referrer string for the URL
        String encodedReferrer = Uri.encode(rawReferrer);

        String deepLinkUrl = "https://play.google.com/d?id=" + _bundleId + "&referrer=" + encodedReferrer;
        Log.d(TAG, "launchPlayOverlay: opening store for bundleId=" + _bundleId);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(deepLinkUrl));
        intent.setPackage("com.android.vending");
        intent.putExtra("overlay", true);
        intent.putExtra("callerId", _activity.getPackageName());

        _state = State.PLAY_OVERLAY;
        _listener.onExpanded();

        // Slide Stage 1 card off-screen if still visible
        if (_stage1Card != null && _stage1Card.getVisibility() == View.VISIBLE)
        {
            float cardOffscreen = Math.max(_stage1Card.getHeight(), dpToPx(_layout.slideOutMinHeightDp)) + dpToPx(_layout.cardEdgeMarginDp);
            _stage1Card.animate()
                    .translationY(cardOffscreen)
                    .setDuration(_layout.slideOutDurationMs)
                    .withEndAction(() -> { if (_stage1Card != null) _stage1Card.setVisibility(View.GONE); })
                    .start();
        }

        if (intent.resolveActivity(_activity.getPackageManager()) != null)
        {
            Log.d(TAG, "launchPlayOverlay: PATH=half-sheet — launching via startActivityForResult");
            if (!_clickUrlFired)
            {
                if (_config != null && _config.clickUrl != null && !_config.clickUrl.isEmpty())
                {
                    _clickUrlFired = true;
                    UAStoreLauncher.fireClickUrl(_activity, _config.clickUrl);
                }
                else
                {
                    Log.e(TAG, "launchPlayOverlay: PATH=half-sheet — clickUrl is null, click not tracked");
                }
            }
            else
            {
                Log.d(TAG, "launchPlayOverlay: PATH=half-sheet — click already tracked for this session");
            }
            _activity.startActivityForResult(intent, REQUEST_PLAY_OVERLAY);
        }
        else
        {
            // Play Store v40.4+ not present — resolve Adjust tracker via UAStoreLauncher
            // so attribution is preserved through the full redirect chain.
            Log.w(TAG, "launchPlayOverlay: PATH=direct-fallback — half-sheet unsupported, resolving via UAStoreLauncher");
            _state = State.COLLAPSED;
            _listener.onCollapsed();
            showStage3Card();

            if (_config == null || _config.clickUrl == null || _config.clickUrl.isEmpty())
            {
                // No clickUrl means attribution is lost but the store can still open via bundleId.
                Log.w(TAG, "launchPlayOverlay: PATH=direct-fallback — clickUrl is null, skipping attribution, opening store via bundleId");
                if (!_isCancelled && !_activity.isFinishing())
                    StoreOpener.openStore(_activity, _bundleId, rawReferrer);
                return;
            }

            if (!_clickUrlFired)
            {
                // First tap — fire click event AND open store via URL resolution
                _clickUrlFired = true;
                UAStoreLauncher.openLink(_activity, _config.clickUrl, new UAStoreLauncher.Callback()
                {
                    @Override
                    public void onSuccess(String packageId)
                    {
                        Log.d(TAG, "launchPlayOverlay: fallback store opened — packageId=" + packageId);
                    }

                    @Override
                    public void onFailed(String reason)
                    {
                        Log.e(TAG, "launchPlayOverlay: fallback UAStoreLauncher failed (" + reason + ")"
                                + " — retrying with StoreOpener bundleId=" + _bundleId);
                        if (!_isCancelled && !_activity.isFinishing())
                            StoreOpener.openStore(_activity, _bundleId, rawReferrer);
                        else
                            Log.w(TAG, "launchPlayOverlay: onFailed — skipping StoreOpener, activity finishing or cancelled");
                    }
                });
            }
            else
            {
                // Click already tracked this session — open store directly without re-firing
                Log.d(TAG, "launchPlayOverlay: fallback — click already tracked, opening store directly bundleId=" + _bundleId);
                StoreOpener.openStore(_activity, _bundleId, rawReferrer);
            }
        }
    }

    // --- Stage 3 ---

    /**
     * Same-size card as Stage 1, bottom-right corner: [GET button].
     * Persists until the ad close button is tapped. GET re-opens the half-sheet.
     */
    private void showStage3Card()
    {
        if (_isCancelled || _activity.isFinishing()) return;
        if (_stage3Card != null)
        {
            Log.d(TAG, "showStage3Card: skipped — already showing");
            return;
        }
        TextView[] btn = new TextView[1];
        _stage3Card = buildPillCard(this::launchPlayOverlay, btn);
        _stage3GetButton = btn[0];
        // Pulse is scheduled from onPlayOverlayResult() each time the user returns from the store

        _rootLayout.addView(_stage3Card);

        // Slide in from below
        _stage3Card.post(() ->
        {
            // cancel() posts to _handler but View.post() uses the View's own message queue —
            // _handler.removeCallbacksAndMessages() cannot cancel this runnable, so guard manually.
            if (_isCancelled || _stage3Card == null || _activity.isFinishing()) return;
            float startY = _stage3Card.getHeight() > 0
                    ? _stage3Card.getHeight() + dpToPx(_layout.cardEdgeMarginDp)
                    : dpToPx(_layout.slideInFallbackDp);
            _stage3Card.setTranslationY(startY);
            _stage3Card.animate()
                    .translationY(0f)
                    .setDuration(_layout.slideInDurationMs)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });
    }

    /**
     * Starts a continuous 1.0→1.1 scale pulse on the GET button.
     * Returns the animator so it can be cancelled on pause/destroy.
     */
    private Animator startPulseAnimation(View target)
    {
        AccelerateDecelerateInterpolator interp = new AccelerateDecelerateInterpolator();

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(target, "scaleX", 1.0f, _layout.pulseScale);
        scaleX.setRepeatMode(ObjectAnimator.REVERSE);
        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleX.setDuration(_layout.pulseDurationMs);
        scaleX.setInterpolator(interp);

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(target, "scaleY", 1.0f, _layout.pulseScale);
        scaleY.setRepeatMode(ObjectAnimator.REVERSE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setDuration(_layout.pulseDurationMs);
        scaleY.setInterpolator(interp);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.start();
        return set;
    }

    private void pulsateStage3Card()
    {
        if (_stage3Card == null) return;
        if (_stage3TapPulseAnimator != null) {
            _stage3TapPulseAnimator.cancel();
            _stage3TapPulseAnimator = null;
            _stage3Card.setScaleX(1f);
            _stage3Card.setScaleY(1f);
        }
        _stage3Card.setPivotX(_stage3Card.getWidth());
        _stage3Card.setPivotY(_stage3Card.getHeight());
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(_stage3Card, "scaleX", 1f, _layout.tapPulseScale, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(_stage3Card, "scaleY", 1f, _layout.tapPulseScale, 1f);
        AnimatorSet pulse = new AnimatorSet();
        pulse.playTogether(scaleX, scaleY);
        pulse.setDuration(_layout.tapPulseDurationMs);
        pulse.setInterpolator(new DecelerateInterpolator());
        _stage3TapPulseAnimator = pulse;
        pulse.start();
    }

    // --- State Transitions ---

    private void peek()
    {
        Log.d(TAG, "state → PEEK");
        _state = State.PEEK;
        _peekTimeMs = System.currentTimeMillis();
        // INVISIBLE (not GONE) so the layout pass measures the card before the animation starts.
        // This guarantees getMeasuredHeight() returns the real height inside the post() callback,
        // so the slide-in always begins exactly one card-height below the final position.
        _stage1Card.setVisibility(View.INVISIBLE);
        _stage1Card.post(() -> {
            if (_isCancelled) return;
            float cardH = _stage1Card.getMeasuredHeight() > 0
                    ? _stage1Card.getMeasuredHeight()
                    : dpToPx(_layout.cardHeightFallbackDp);
            float startY = cardH + dpToPx(_layout.cardEdgeMarginDp);
            _stage1Card.setTranslationY(startY);
            _stage1Card.setVisibility(View.VISIBLE);
            _stage1Card.animate()
                    .translationY(0f)
                    .setDuration(_layout.slideInDurationMs)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });
        // Start pulse countdown from the moment peek() is called (not inside post — delay is long
        // enough that the card is always visible before the pulse fires)
        if (_config != null && !_config.disablePulse) {
            long pulseDelayMs = (_config.pulseStartDelaySec > 0 ? _config.pulseStartDelaySec : 5) * 1000L;
            _stage1PulseRunnable = () -> {
                if (!_isCancelled && _stage1GetButton != null)
                    _stage1PulseAnimator = startPulseAnimation(_stage1GetButton);
            };
            _handler.postDelayed(_stage1PulseRunnable, pulseDelayMs);
        }
        _listener.onPeeked();
    }

    private void scheduleStage3Pulse()
    {
        if (_stage3PulseRunnable != null) _handler.removeCallbacks(_stage3PulseRunnable);
        if (_stage3PulseAnimator != null) { _stage3PulseAnimator.cancel(); _stage3PulseAnimator = null; }
        if (_stage3GetButton != null) { _stage3GetButton.setScaleX(1f); _stage3GetButton.setScaleY(1f); }
        if (_config == null || _config.disablePulse) return;
        long pulseDelayMs = (_config.pulseStartDelaySec > 0 ? _config.pulseStartDelaySec : 5) * 1000L;
        _stage3PulseRunnable = () -> {
            if (!_isCancelled && _stage3GetButton != null)
                _stage3PulseAnimator = startPulseAnimation(_stage3GetButton);
        };
        _handler.postDelayed(_stage3PulseRunnable, pulseDelayMs);
    }

    // --- Attribution ---

    /**
     * Extracts the Adjust tracker token from a URL
     * Returns the last non-empty path segment, or null if not found.
     */
    private static String extractAdjustToken(String clickUrl)
    {
        if (clickUrl == null || clickUrl.isEmpty()) return null;
        try
        {
            String path = Uri.parse(clickUrl).getLastPathSegment();
            return (path != null && !path.isEmpty()) ? path : null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    // --- Helpers ---

    private int dpToPx(float dp)
    {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                _activity.getResources().getDisplayMetrics());
    }
}
