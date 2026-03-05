package com.ua.toolkit.popup;

import com.ua.toolkit.AdConfig;
import com.ua.toolkit.store.StoreOpener;

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
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
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
    private String _appName;
    private AdConfig _config;

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
        _appName = config.appName;
        Log.d(TAG, "attach: bundleId=" + _bundleId + " appName=" + _appName
                + " iconValid=" + config.hasValidIcon()
                + " peekDelay=" + config.peekDelay
                + " clickUrl=" + config.clickUrl);

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
    }

    public void cancel()
    {
        Log.d(TAG, "cancel: state=" + _state + " stage1=" + (_stage1Card != null) + " stage3=" + (_stage3Card != null));
        _isCancelled = true;
        _handler.removeCallbacksAndMessages(null);
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
        int cardWidth = (int) (getScreenWidth() * 0.24f);

        _stage1Card = new LinearLayout(_activity);
        _stage1Card.setOrientation(LinearLayout.HORIZONTAL);
        _stage1Card.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        _stage1Card.setPadding(dpToPx(10), dpToPx(12), dpToPx(10), dpToPx(12));

        // Stage 1 popup
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#80000000"));
        cardBg.setCornerRadius(dpToPx(100)); // pill shape
        _stage1Card.setBackground(cardBg);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        cardLp.rightMargin = dpToPx(24);
        cardLp.bottomMargin = dpToPx(24);
        _stage1Card.setLayoutParams(cardLp);

        // App name
        TextView nameText = new TextView(_activity);
        String displayName = (_appName != null && !_appName.isEmpty()) ? _appName
                : (_bundleId != null ? _bundleId : "");
        Log.d(TAG, "buildStage1Card: displayName=\"" + displayName + "\" cardWidthPx=" + cardWidth);
        nameText.setText(displayName);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(13);
        nameText.setTypeface(Typeface.DEFAULT_BOLD);
        nameText.setMaxLines(1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            nameText.setAutoSizeTextTypeUniformWithConfiguration(7, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        }
        else
        {
            nameText.setEllipsize(TextUtils.TruncateAt.END);
        }
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                dpToPx(48), LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.rightMargin = dpToPx(8);
        nameText.setLayoutParams(nameLp);
        _stage1Card.addView(nameText);

        addGetButton(_stage1Card, () ->
        {
            if (!_isAdClicked)
            {
                _isAdClicked = true;
                _listener.onAdClicked();
            }
            launchPlayOverlay();
        });
    }

    private void addGetButton(LinearLayout parent, Runnable onClick)
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
        getBtn.setPadding(dpToPx(12), 0, dpToPx(12), 0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.gravity = Gravity.CENTER_VERTICAL;
        getBtn.setLayoutParams(params);
        getBtn.setOnClickListener(v ->
        {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            onClick.run();
        });
        parent.addView(getBtn);
    }

    // --- Stage 2: Play Store Native Sheet overlay

    private void launchPlayOverlay()
    {
        if (_state != State.PEEK && _state != State.COLLAPSED) return;

        if (_bundleId == null || _bundleId.isEmpty())
        {
            Log.e(TAG, "launchPlayOverlay: bundleId is empty — cannot launch half-sheet");
            return;
        }

        // Extract Adjust token from clickUrl and embed as referrer for attribution.
        // Google Play's Install Referrer API delivers it to the installed app on first launch.
        String token = extractAdjustToken(_config != null ? _config.clickUrl : null);
        String encodedReferrer = (token != null) ? Uri.encode("adjust_tracker=" + token) : null;
        String deepLinkUrl = "https://play.google.com/d?id=" + _bundleId
                + (encodedReferrer != null ? "&referrer=" + encodedReferrer : "");
        Log.d(TAG, "launchPlayOverlay: bundleId=" + _bundleId
                + " token=" + token
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
            Log.d(TAG, "launchPlayOverlay: launching half-sheet via startActivityForResult");
            _activity.startActivityForResult(intent, REQUEST_PLAY_OVERLAY);
        }
        else
        {
            // Play Store v40.4+ not present — open store directly and skip to Stage 3
            Log.w(TAG, "launchPlayOverlay: half-sheet not supported, falling back to StoreOpener");
            StoreOpener.openStore(_activity, _bundleId, null);
            _state = State.COLLAPSED;
            _listener.onCollapsed();
            showStage3Card();
        }
    }

    // --- Stage 3 ---

    /**
     * Same-size card as Stage 1, bottom-right corner: [app name flex] [GET button].
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
        Log.d(TAG, "showStage3Card: appName=\"" + _appName + "\" bundleId=" + _bundleId);

        int cardWidth = (int) (getScreenWidth() * 0.24f);

        LinearLayout card = new LinearLayout(_activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        card.setPadding(dpToPx(10), dpToPx(12), dpToPx(10), dpToPx(12));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#80000000"));
        cardBg.setCornerRadius(dpToPx(100)); // pill shape
        card.setBackground(cardBg);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        cardLp.rightMargin  = dpToPx(24);
        cardLp.bottomMargin = dpToPx(24);
        card.setLayoutParams(cardLp);

        // App name — fills space between card edge and GET button
        TextView nameText = new TextView(_activity);
        String displayName = (_appName != null && !_appName.isEmpty()) ? _appName
                : (_bundleId != null ? _bundleId : "");
        nameText.setText(displayName);
        nameText.setTextColor(Color.WHITE);
        nameText.setTextSize(13);
        nameText.setTypeface(Typeface.DEFAULT_BOLD);
        nameText.setMaxLines(1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            nameText.setAutoSizeTextTypeUniformWithConfiguration(7, 13, 1, TypedValue.COMPLEX_UNIT_SP);
        }
        else
        {
            nameText.setEllipsize(TextUtils.TruncateAt.END);
        }
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                dpToPx(48), LinearLayout.LayoutParams.WRAP_CONTENT);
        nameLp.rightMargin = dpToPx(8);
        nameText.setLayoutParams(nameLp);
        card.addView(nameText);

        // GET button — re-opens the half-sheet overlay
        addGetButton(card, this::launchPlayOverlay);

        _stage3Card = card;
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
        _listener.onPeeked();
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

    private int getScreenWidth()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            return _activity.getWindowManager().getCurrentWindowMetrics().getBounds().width();
        }
        DisplayMetrics metrics = new DisplayMetrics();
        //noinspection deprecation
        _activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.widthPixels;
    }

    private int dpToPx(float dp)
    {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                _activity.getResources().getDisplayMetrics());
    }
}
