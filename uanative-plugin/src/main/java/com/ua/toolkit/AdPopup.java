package com.ua.toolkit;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class AdPopup
{
    public interface Listener
    {
        void onPeeked();       // sheet first becomes visible — hide any overlapping ad UI
        void onDismissed();    // X button closed the popup — restore hidden ad UI
        void onExpanded();     // sheet dragged to 80% — pause video
        void onCollapsed();    // sheet dragged back to peek — resume video
        void onInstallClicked();
    }

    private static final String TAG = "AdPopup";
    private static final int PEEK_DELAY_MS = 5000;
    private static final int ANIM_DURATION_MS = 280;
    private static final float SCRIM_MAX_ALPHA = 0.5f;

    private enum State { HIDDEN, PEEK, EXPANDED }

    private final Activity _activity;
    private final FrameLayout _rootLayout;
    private final Listener _listener;
    private final Handler _handler = new Handler(Looper.getMainLooper());

    private View _scrim;
    private LinearLayout _sheet;
    private WebView _webView;

    private int _peekHeight;
    private int _sheetHeight;
    private boolean _isCancelled = false;
    private State _state = State.HIDDEN;

    private float _dragStartY;
    private float _dragStartTranslation;
    private AnimatorSet _runningAnimator;

    public AdPopup(Activity activity, FrameLayout rootLayout, Listener listener)
    {
        _activity = activity;
        _rootLayout = rootLayout;
        _listener = listener;
    }

    // --- Public API ---

    public void attach(AdConfig config)
    {
        int screenHeight = getScreenHeight();
        _peekHeight = dpToPx(120);
        _sheetHeight = (int) (screenHeight * 0.8f);

        buildScrim(screenHeight);
        buildSheet(config);

        // Insert above video (index 0) but below top controls (timer, mute, close)
        _rootLayout.addView(_scrim, 1);
        _rootLayout.addView(_sheet, 2);

        _sheet.setTranslationY(_sheetHeight);
        _scrim.setAlpha(0f);
        _scrim.setVisibility(View.INVISIBLE);
    }

    public void schedulePeek()
    {
        _handler.postDelayed(() ->
        {
            if (!_isCancelled && _state == State.HIDDEN) peek();
        }, PEEK_DELAY_MS);
    }

    public boolean isExpanded()
    {
        return _state == State.EXPANDED;
    }

    public boolean handleBackPress()
    {
        if (_state == State.EXPANDED)
        {
            collapseToPeek();
            return true;
        }
        return false;
    }

    public void cancel()
    {
        _isCancelled = true;
        _handler.removeCallbacksAndMessages(null);
        if (_runningAnimator != null)
        {
            _runningAnimator.cancel();
            _runningAnimator = null;
        }
        if (_webView != null)
        {
            _webView.destroy();
            _webView = null;
        }
        if (_scrim != null)
        {
            _rootLayout.removeView(_scrim);
            _scrim = null;
        }
        if (_sheet != null)
        {
            _rootLayout.removeView(_sheet);
            _sheet = null;
        }
    }

    // --- View Construction ---

    private void buildScrim(int screenHeight)
    {
        _scrim = new View(_activity);
        _scrim.setBackgroundColor(Color.BLACK);
        _scrim.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, screenHeight));
    }

    private void buildSheet(AdConfig config)
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        // Rounded top corners only
        bg.setCornerRadii(new float[]
        {
            dpToPx(16), dpToPx(16), // top-left
            dpToPx(16), dpToPx(16), // top-right
            0, 0,                   // bottom-right
            0, 0                    // bottom-left
        });

        _sheet = new LinearLayout(_activity);
        _sheet.setOrientation(LinearLayout.VERTICAL);
        _sheet.setBackground(bg);
        _sheet.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        _sheet.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, _sheetHeight, Gravity.BOTTOM));

        addHeaderRow();
        addIconView(config);
        addInstallButton();
        addStoreWebView(config);
        setupDragListener();
    }

    // Header row: drag-handle pill centered, X close button pinned to the right
    private void addHeaderRow()
    {
        FrameLayout header = new FrameLayout(_activity);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44)));

        // Drag handle pill — centered in the header
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Color.parseColor("#CCCCCC"));
        pillBg.setCornerRadius(dpToPx(3));

        View pill = new View(_activity);
        pill.setBackground(pillBg);
        pill.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(40), dpToPx(4), Gravity.CENTER));

        // X close button — right-aligned, full header height for easy tapping
        TextView closeBtn = new TextView(_activity);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.parseColor("#999999"));
        closeBtn.setTextSize(14);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams closeBtnParams = new FrameLayout.LayoutParams(
                dpToPx(44), dpToPx(44), Gravity.CENTER_VERTICAL | Gravity.END);
        closeBtnParams.rightMargin = dpToPx(4);
        closeBtn.setLayoutParams(closeBtnParams);
        closeBtn.setOnClickListener(v -> collapseToHidden());

        header.addView(pill);
        header.addView(closeBtn);

        _sheet.addView(header);
    }

    private void addIconView(AdConfig config)
    {
        ImageView iconView = new ImageView(_activity);
        int iconSize = dpToPx(56);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(iconSize, iconSize);
        params.topMargin = dpToPx(16);
        params.bottomMargin = dpToPx(16);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        iconView.setLayoutParams(params);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        if (config.hasValidIcon())
        {
            Bitmap icon = BitmapFactory.decodeFile(config.iconPath);
            if (icon != null) iconView.setImageBitmap(icon);
        }

        iconView.setVisibility(config.hasValidIcon() ? View.VISIBLE : View.GONE);
        _sheet.addView(iconView);
    }

    private void addInstallButton()
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#4CAF50"));
        bg.setCornerRadius(dpToPx(6));

        TextView installBtn = new TextView(_activity);
        installBtn.setText("INSTALL");
        installBtn.setTextColor(Color.WHITE);
        installBtn.setTextSize(18);
        installBtn.setTypeface(Typeface.DEFAULT_BOLD);
        installBtn.setGravity(Gravity.CENTER);
        installBtn.setBackground(bg);
        installBtn.setPadding(dpToPx(24), dpToPx(12), dpToPx(24), dpToPx(12));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dpToPx(8);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        installBtn.setLayoutParams(params);
        installBtn.setOnClickListener(v -> _listener.onInstallClicked());

        _sheet.addView(installBtn);
    }

    private void addStoreWebView(AdConfig config)
    {
        if (config.clickUrl.isEmpty()) return;

        // FrameLayout wrapper — weight=1 fills all remaining sheet height below the install button
        FrameLayout webContainer = new FrameLayout(_activity);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerParams.topMargin = dpToPx(12);
        webContainer.setLayoutParams(containerParams);

        // Loading spinner shown until the page finishes loading
        ProgressBar spinner = new ProgressBar(_activity);
        spinner.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        _webView = new WebView(_activity);
        _webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        _webView.getSettings().setJavaScriptEnabled(true);
        _webView.getSettings().setDomStorageEnabled(true);
        _webView.getSettings().setLoadWithOverviewMode(true);
        _webView.getSettings().setUseWideViewPort(true);
        _webView.setWebViewClient(new WebViewClient()
        {
            @Override
            public void onPageFinished(WebView view, String url)
            {
                spinner.setVisibility(View.GONE);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
            {
                String url = request.getUrl().toString();
                // Follow http/https redirects (Play Store web page, tracking links, etc.)
                if (url.startsWith("https://") || url.startsWith("http://"))
                    return false;
                // Convert market:// deep links to the Play Store web equivalent
                if (url.startsWith("market://details"))
                {
                    view.loadUrl(url.replace("market://details",
                            "https://play.google.com/store/apps/details"));
                    return true;
                }
                // Block all other schemes (intent://, tel://, etc.)
                return true;
            }
        });
        _webView.loadUrl(config.clickUrl);

        webContainer.addView(_webView);
        webContainer.addView(spinner);
        _sheet.addView(webContainer);
    }

    // --- Drag Handling ---

    private void setupDragListener()
    {
        _sheet.setOnTouchListener((v, event) ->
        {
            if (_state == State.HIDDEN) return false;

            switch (event.getAction())
            {
                case MotionEvent.ACTION_DOWN:
                    _dragStartY = event.getRawY();
                    _dragStartTranslation = _sheet.getTranslationY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float delta = event.getRawY() - _dragStartY;
                    float newTranslation = _dragStartTranslation + delta;
                    // Clamp: cannot go above expanded (0) or below peek
                    float peekTranslation = _sheetHeight - _peekHeight;
                    newTranslation = Math.max(0f, Math.min(newTranslation, peekTranslation));
                    _sheet.setTranslationY(newTranslation);
                    updateScrimAlpha(newTranslation);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    snapSheet(_sheet.getTranslationY());
                    return true;
            }
            return false;
        });
    }

    private void updateScrimAlpha(float translationY)
    {
        float peekTranslation = _sheetHeight - _peekHeight;
        if (peekTranslation <= 0) return;
        float progress = 1f - (translationY / peekTranslation);
        progress = Math.max(0f, Math.min(1f, progress));
        _scrim.setAlpha(progress * SCRIM_MAX_ALPHA);
    }

    private void snapSheet(float currentTranslation)
    {
        // Midpoint between fully expanded (0) and peek position
        float midpoint = (_sheetHeight - _peekHeight) * 0.5f;
        if (currentTranslation < midpoint)
        {
            expand();
        }
        else
        {
            collapseToPeek();
        }
    }

    // --- State Transitions ---

    private void peek()
    {
        _state = State.PEEK;
        _scrim.setVisibility(View.VISIBLE);
        animateSheet(_sheetHeight - _peekHeight, 0f, null);
        _listener.onPeeked();
    }

    private void expand()
    {
        if (_state == State.EXPANDED) return;
        _state = State.EXPANDED;
        _scrim.setVisibility(View.VISIBLE);
        animateSheet(0f, SCRIM_MAX_ALPHA, null);
        _listener.onExpanded();
    }

    private void collapseToPeek()
    {
        boolean wasExpanded = _state == State.EXPANDED;
        _state = State.PEEK;
        animateSheet(_sheetHeight - _peekHeight, 0f, null);
        if (wasExpanded) _listener.onCollapsed();
    }

    private void collapseToHidden()
    {
        boolean wasExpanded = _state == State.EXPANDED;
        _state = State.HIDDEN;
        // If video was paused by expand, resume it immediately as popup slides away
        if (wasExpanded) _listener.onCollapsed();
        animateSheet(_sheetHeight, 0f, () ->
        {
            if (_scrim != null) _scrim.setVisibility(View.INVISIBLE);
            _listener.onDismissed();
        });
    }

    // --- Animation ---

    private void animateSheet(float targetTranslation, float targetScrimAlpha, Runnable onEnd)
    {
        if (_runningAnimator != null)
        {
            _runningAnimator.cancel();
            _runningAnimator = null;
        }

        ObjectAnimator sheetAnim = ObjectAnimator.ofFloat(_sheet, "translationY", targetTranslation);
        ObjectAnimator scrimAnim = ObjectAnimator.ofFloat(_scrim, "alpha", targetScrimAlpha);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(sheetAnim, scrimAnim);
        set.setDuration(ANIM_DURATION_MS);
        set.setInterpolator(new DecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter()
        {
            @Override
            public void onAnimationEnd(Animator animation)
            {
                _runningAnimator = null;
                if (onEnd != null) onEnd.run();
            }
        });

        _runningAnimator = set;
        set.start();
    }

    // --- Helpers ---

    private int getScreenHeight()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            return _activity.getWindowManager().getCurrentWindowMetrics().getBounds().height();
        }
        DisplayMetrics metrics = new DisplayMetrics();
        //noinspection deprecation
        _activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.heightPixels;
    }

    private int dpToPx(float dp)
    {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                _activity.getResources().getDisplayMetrics());
    }
}
