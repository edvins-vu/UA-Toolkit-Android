package com.ua.toolkit.popup;

import com.ua.toolkit.AdConfig;
import com.ua.toolkit.UAStoreLauncher;
import com.ua.toolkit.store.StoreOpener;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private static final int ANIM_DURATION_MS = 280;

    private enum State { HIDDEN, PEEK, PLAY_OVERLAY, COLLAPSED }

    private final Activity _activity;
    private final FrameLayout _rootLayout;
    private final Listener _listener;
    private final Handler _handler = new Handler(Looper.getMainLooper());

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
    private Runnable _stage1PulseRunnable;
    private Runnable _stage3PulseRunnable;

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
        Log.d(TAG, "attach: bundleId=" + _bundleId
                + " peekDelay=" + config.peekDelay
                + " clickUrl=" + config.clickUrl);

        _rootLayout.setClipChildren(false);
        buildStage1Card(config);

        // Insert at index 1 (above video surface at 0, below UIManager controls)
        _rootLayout.addView(_stage1Card, 1);
        _stage1Card.setTranslationY(dpToPx(200));
        _stage1Card.setVisibility(View.INVISIBLE);
    }

    public void schedulePeek(int delaySeconds)
    {
        Log.d(TAG, "schedulePeek: will peek in " + delaySeconds + "s");
        _handler.postDelayed(() ->
        {
            if (!_isCancelled && _state == State.HIDDEN) peek();
            else Log.d(TAG, "schedulePeek: skipped (cancelled=" + _isCancelled + " state=" + _state + ")");
        }, delaySeconds * 1000L);
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

    /** Called by AdActivity when the user taps the video surface. */
    public void handleVideoTap()
    {
        Log.d(TAG, "handleVideoTap: state=" + _state + " isCancelled=" + _isCancelled);
        if (_isCancelled) return;
        switch (_state)
        {
            case HIDDEN:
                // Cancel scheduled auto-peek and show Stage 1 immediately
                _handler.removeCallbacksAndMessages(null);
                peek();
                break;
            case PEEK:
                // Guard: if the card is still animating in, ignore the tap so the user
                // actually sees the popup before the store opens.
                if (System.currentTimeMillis() - _peekTimeMs < ANIM_DURATION_MS)
                {
                    Log.d(TAG, "handleVideoTap: PEEK — tap within slide-in window, ignoring");
                    break;
                }
                // Video tap while Stage 1 visible → same outcome as GET button
                if (!_isAdClicked)
                {
                    _isAdClicked = true;
                    _listener.onAdClicked();
                }
                launchPlayOverlay();
                break;
            case PLAY_OVERLAY:
                Log.d(TAG, "handleVideoTap: ignored — half-sheet is open");
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
        int bottom = bottomInset + dpToPx(24);
        int right  = rightInset  + dpToPx(24);
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

    public void cancel()
    {
        Log.d(TAG, "cancel: state=" + _state + " stage1=" + (_stage1Card != null) + " stage3=" + (_stage3Card != null));
        _isCancelled = true;
        UAStoreLauncher.cancel(); // stop any in-progress fallback store resolution
        _handler.removeCallbacksAndMessages(null);
        if (_stage1PulseAnimator != null) { _stage1PulseAnimator.cancel(); _stage1PulseAnimator = null; }
        if (_stage3PulseAnimator != null) { _stage3PulseAnimator.cancel(); _stage3PulseAnimator = null; }
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
        card.setPadding(dpToPx(10), dpToPx(12), dpToPx(10), dpToPx(12));
        card.setClipChildren(false);
        card.setClipToPadding(false);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#80000000"));
        cardBg.setCornerRadius(dpToPx(100)); // pill shape
        card.setBackground(cardBg);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        cardLp.rightMargin  = _cardRightInset  + dpToPx(24);
        cardLp.bottomMargin = _cardBottomInset + dpToPx(24);
        card.setLayoutParams(cardLp);

        buttonOut[0] = addGetButton(card, onGetClick);
        return card;
    }

    private TextView addGetButton(LinearLayout parent, Runnable onClick)
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#4CAF50"));
        bg.setCornerRadius(dpToPx(100)); // pill shape

        TextView getBtn = new TextView(_activity);
        getBtn.setText(_config != null ? _config.getButtonText : "GET");
        getBtn.setTextColor(Color.WHITE);
        getBtn.setTextSize(14);
        getBtn.setTypeface(Typeface.DEFAULT_BOLD);
        getBtn.setGravity(Gravity.CENTER);
        getBtn.setBackground(bg);

        // 1. Reduce horizontal padding since we are using a fixed width now
        getBtn.setPadding(0, 0, 0, 0);

        // 2. Set a fixed Width (e.g., 100dp to 120dp) instead of WRAP_CONTENT
        // This maintains the large "Pill" look even without the App Name text.
        int buttonWidth = dpToPx(165);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                buttonWidth,
                dpToPx(44)
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

    private void launchPlayOverlay()
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
        String tracker = extractAdjustToken(_config != null ? _config.clickUrl : null);
        String source = "adjust_store";
        String rawReferrer = "adjust_tracker=" + tracker + "&utm_source=" + source;
        // Encode the entire referrer string for the URL
        String encodedReferrer = Uri.encode(rawReferrer);

        String deepLinkUrl = "https://play.google.com/d?id=" + _bundleId + "&referrer=" + encodedReferrer;
        Log.d(TAG, "launchPlayOverlay: bundleId=" + _bundleId
                + " token=" + tracker
                + " deepLinkUrl=" + deepLinkUrl);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(deepLinkUrl));
        intent.setPackage("com.android.vending");
        intent.putExtra("overlay", true);
        intent.putExtra("callerId", _activity.getPackageName());

        Log.d(TAG, "state → PLAY_OVERLAY");
        _state = State.PLAY_OVERLAY;
        _listener.onExpanded();

        // Slide Stage 1 card off-screen if still visible
        if (_stage1Card != null && _stage1Card.getVisibility() == View.VISIBLE)
        {
            float cardOffscreen = Math.max(_stage1Card.getHeight(), dpToPx(100)) + dpToPx(24);
            _stage1Card.animate()
                    .translationY(cardOffscreen)
                    .setDuration(200)
                    .withEndAction(() -> _stage1Card.setVisibility(View.GONE))
                    .start();
        }

        if (intent.resolveActivity(_activity.getPackageManager()) != null)
        {
            Log.d(TAG, "launchPlayOverlay: PATH=half-sheet — launching via startActivityForResult");
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

            if (_config != null && _config.clickUrl != null && !_config.clickUrl.isEmpty())
            {
                Log.d(TAG, "launchPlayOverlay: fallback — UAStoreLauncher.openLink clickUrl=" + _config.clickUrl);
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
                Log.w(TAG, "launchPlayOverlay: fallback — no clickUrl, using StoreOpener directly bundleId=" + _bundleId);
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
        Log.d(TAG, "showStage3Card: bundleId=" + _bundleId);

        TextView[] btn = new TextView[1];
        _stage3Card = buildPillCard(this::launchPlayOverlay, btn);
        _stage3GetButton = btn[0];
        // Pulse is scheduled from onPlayOverlayResult() each time the user returns from the store

        _rootLayout.addView(_stage3Card);

        // Slide in from below
        _stage3Card.post(() ->
        {
            float startY = _stage3Card.getHeight() > 0
                    ? _stage3Card.getHeight() + dpToPx(24)
                    : dpToPx(200);
            _stage3Card.setTranslationY(startY);
            _stage3Card.animate()
                    .translationY(0f)
                    .setDuration(ANIM_DURATION_MS)
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

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(target, "scaleX", 1.0f, 1.05f);
        scaleX.setRepeatMode(ObjectAnimator.REVERSE);
        scaleX.setRepeatCount(ObjectAnimator.INFINITE);
        scaleX.setDuration(600);
        scaleX.setInterpolator(interp);

        ObjectAnimator scaleY = ObjectAnimator.ofFloat(target, "scaleY", 1.0f, 1.05f);
        scaleY.setRepeatMode(ObjectAnimator.REVERSE);
        scaleY.setRepeatCount(ObjectAnimator.INFINITE);
        scaleY.setDuration(600);
        scaleY.setInterpolator(interp);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.start();
        return set;
    }

    private void pulsateStage3Card()
    {
        Log.d(TAG, "pulsateStage3Card: stage3Present=" + (_stage3Card != null));
        if (_stage3Card == null) return;
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(_stage3Card, "scaleX", 1f, 1.04f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(_stage3Card, "scaleY", 1f, 1.04f, 1f);
        AnimatorSet pulse = new AnimatorSet();
        pulse.playTogether(scaleX, scaleY);
        pulse.setDuration(350);
        pulse.setInterpolator(new DecelerateInterpolator());
        pulse.start();
    }

    // --- State Transitions ---

    private void peek()
    {
        Log.d(TAG, "state → PEEK");
        _state = State.PEEK;
        _peekTimeMs = System.currentTimeMillis();
        _stage1Card.setTranslationY(dpToPx(200));
        _stage1Card.setVisibility(View.VISIBLE);
        _stage1Card.post(() ->
        {
            float startY = _stage1Card.getHeight() > 0
                    ? _stage1Card.getHeight() + dpToPx(24)
                    : dpToPx(200);
            _stage1Card.setTranslationY(startY);
            _stage1Card.animate()
                    .translationY(0f)
                    .setDuration(ANIM_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });
        // Start pulse countdown from the moment the card is visible
        _stage1PulseRunnable = () -> {
            if (!_isCancelled && _stage1GetButton != null)
                _stage1PulseAnimator = startPulseAnimation(_stage1GetButton);
        };
        _handler.postDelayed(_stage1PulseRunnable, 5000L);
        _listener.onPeeked();
    }

    private void scheduleStage3Pulse()
    {
        if (_stage3PulseRunnable != null) _handler.removeCallbacks(_stage3PulseRunnable);
        if (_stage3PulseAnimator != null) { _stage3PulseAnimator.cancel(); _stage3PulseAnimator = null; }
        if (_stage3GetButton != null) { _stage3GetButton.setScaleX(1f); _stage3GetButton.setScaleY(1f); }
        _stage3PulseRunnable = () -> {
            if (!_isCancelled && _stage3GetButton != null)
                _stage3PulseAnimator = startPulseAnimation(_stage3GetButton);
        };
        _handler.postDelayed(_stage3PulseRunnable, 5000L);
    }

    // --- Attribution ---

    /**
     * Extracts the Adjust tracker token from a URL like https://app.adjust.com/1w3tf31m.
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
