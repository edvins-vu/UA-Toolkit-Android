package com.ua.toolkit.popup;

import com.ua.toolkit.AdConfig;
import com.ua.toolkit.store.StoreOpener;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;

import android.content.BroadcastReceiver;
import android.content.Context;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebChromeClient;
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
        void onPeeked();       // Stage 1 first becomes visible
        void onDismissed();    // Popup fully done — ad should close
        void onExpanded();     // Stage 2 shown — pause video
        void onCollapsed();    // Stage 2 → Stage 3 — resume video
        void onAdClicked();    // Stage 1 GET tapped (or video tap from Stage 1) — fire analytics
    }

    private static final String TAG = "AdPopup";
    private static final int ANIM_DURATION_MS = 280;
    private static final float SCRIM_MAX_ALPHA = 0.5f;

    private enum State { HIDDEN, PEEK, EXPANDED, NATIVE_FALLBACK }

    private final Activity _activity;
    private final FrameLayout _rootLayout;
    private final Listener _listener;
    private final Handler _handler = new Handler(Looper.getMainLooper());

    private View _scrim;
    private LinearLayout _sheet;
    private LinearLayout _stage1Card; // compact bottom-right card for Stage 1
    private WebView _webView;

    private int _sheetHeight;
    private boolean _isCancelled = false;
    private State _state = State.HIDDEN;

    private float _dragStartY;
    private float _dragStartTranslation;
    private AnimatorSet _runningAnimator;

    private boolean _isAdClicked = false;
    private boolean _storePageLoaded = false;
    private String _targetPackage;
    private String _appTitle;   // captured from the Play Store page title in Stage 2
    private AdConfig _config;
    private View _stage3Card;

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
        _targetPackage = extractTargetPackage(config.clickUrl);

        int screenHeight = getScreenHeight();
        _sheetHeight = (int) (screenHeight * 0.8f);

        buildScrim(screenHeight);
        buildStage1Card(config);
        buildSheet();

        // Insert above video (index 0) but below top controls (timer, mute, close)
        _rootLayout.addView(_scrim, 1);
        _rootLayout.addView(_stage1Card, 2);
        _rootLayout.addView(_sheet, 3);

        // Stage 1 card starts below the screen edge; sheet starts fully off-screen
        _stage1Card.setTranslationY(dpToPx(200));
        _stage1Card.setVisibility(View.INVISIBLE);
        _sheet.setTranslationY(_sheetHeight);
        _scrim.setAlpha(0f);
        _scrim.setVisibility(View.INVISIBLE);
    }

    public void schedulePeek(int delaySeconds)
    {
        _handler.postDelayed(() ->
        {
            if (!_isCancelled && _state == State.HIDDEN) peek();
        }, delaySeconds * 1000L);
    }

    public boolean isExpanded()
    {
        return _state == State.EXPANDED;
    }

    public boolean handleBackPress()
    {
        if (_state == State.EXPANDED)
        {
            transitionToNativeFallback();
            return true;
        }
        if (_state == State.NATIVE_FALLBACK)
        {
            // Don't close from back — only the AdActivity close button should dismiss the ad.
            // Return false so AdActivity's handleBackNavigation() checks isCloseButtonVisible().
            return false;
        }
        return false;
    }

    /** Called by AdActivity when the user taps the video surface. */
    public void handleVideoTap()
    {
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
                expandToFull();
                break;
            case EXPANDED:
                // Already in Stage 2 — ignore
                break;
            case NATIVE_FALLBACK:
                // Stage 3 is showing — draw attention to it
                pulsateNativeFallback();
                break;
        }
    }

    public void cancel()
    {
        _isCancelled = true;
        _handler.removeCallbacksAndMessages(null);
        if (_stage3Card != null)
        {
            _stage3Card.animate().cancel();
            _rootLayout.removeView(_stage3Card);
            _stage3Card = null;
        }
        if (_runningAnimator != null)
        {
            _runningAnimator.cancel();
            _runningAnimator = null;
        }
        destroyWebView();
        if (_stage1Card != null)
        {
            _stage1Card.animate().cancel();
            _rootLayout.removeView(_stage1Card);
            _stage1Card = null;
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

    private void buildSheet()
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
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

        // No Stage 1 content here — Stage 1 lives in _stage1Card.
        // Stage 2 header + WebView are added dynamically in expandToFull().
        setupDragListener();
    }

    /**
     * Builds the compact Stage 1 card: icon + GET button side by side,
     * pinned to the bottom-right corner, max 30% of screen width.
     */
    private void buildStage1Card(AdConfig config)
    {
        int cardWidth = (int) (getScreenWidth() * 0.30f);

        _stage1Card = new LinearLayout(_activity);
        _stage1Card.setOrientation(LinearLayout.HORIZONTAL);
        _stage1Card.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        _stage1Card.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dpToPx(16));
        _stage1Card.setBackground(cardBg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            _stage1Card.setElevation(dpToPx(8));
        }

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                cardWidth, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        cardLp.rightMargin = dpToPx(16);
        cardLp.bottomMargin = dpToPx(16);
        _stage1Card.setLayoutParams(cardLp);

        if (config.hasValidIcon())
        {
            ImageView iconView = new ImageView(_activity);
            int iconSize = dpToPx(40);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconLp.rightMargin = dpToPx(8);
            iconView.setLayoutParams(iconLp);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Bitmap icon = BitmapFactory.decodeFile(config.iconPath);
            if (icon != null) iconView.setImageBitmap(icon);
            _stage1Card.addView(iconView);
        }

        addGetButton(_stage1Card);
    }

    private void addGetButton(LinearLayout parent)
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#4CAF50"));
        bg.setCornerRadius(dpToPx(6));

        TextView getBtn = new TextView(_activity);
        getBtn.setText("GET");
        getBtn.setTextColor(Color.WHITE);
        getBtn.setTextSize(14);
        getBtn.setTypeface(Typeface.DEFAULT_BOLD);
        getBtn.setGravity(Gravity.CENTER);
        getBtn.setBackground(bg);
        getBtn.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.gravity = Gravity.CENTER_VERTICAL;
        getBtn.setLayoutParams(params);

        getBtn.setOnClickListener(v ->
        {
            if (!_isAdClicked)
            {
                _isAdClicked = true;
                _listener.onAdClicked();
            }
            expandToFull();
        });

        parent.addView(getBtn);
    }

    // --- Stage 2 Construction ---

    /**
     * Transitions from Stage 1 (PEEK) to Stage 2 (EXPANDED).
     * Dynamically adds the header (drag handle) and WebView to the sheet,
     * hides the Stage 1 GET button, loads the store URL, and fires onExpanded().
     */
    private void expandToFull()
    {
        if (_state != State.PEEK) return;
        _state = State.EXPANDED;

        // Slide Stage 1 card off-screen while the full sheet animates in
        float cardOffscreen = Math.max(_stage1Card.getHeight(), dpToPx(100)) + dpToPx(24);
        _stage1Card.animate()
                .translationY(cardOffscreen)
                .setDuration(200)
                .withEndAction(() -> _stage1Card.setVisibility(View.GONE))
                .start();

        // Add drag handle header at top of sheet (index 0)
        _sheet.addView(buildHeaderRow(), 0);

        // Add WebView container at the end (fills remaining space)
        addStoreWebView();

        // Load the store URL — Adjust attribution fires here, not on attach
        if (_webView != null && _config != null && !_config.clickUrl.isEmpty())
        {
            _webView.loadUrl(_config.clickUrl);
        }

        // Animate to full height and notify listener
        _scrim.setVisibility(View.VISIBLE);
        animateSheet(0f, SCRIM_MAX_ALPHA, null);
        _listener.onExpanded();
    }

    /** Builds a drag-handle-only header row for Stage 2. */
    private View buildHeaderRow()
    {
        FrameLayout header = new FrameLayout(_activity);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44)));

        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setColor(Color.parseColor("#CCCCCC"));
        pillBg.setCornerRadius(dpToPx(3));

        View pill = new View(_activity);
        pill.setBackground(pillBg);
        pill.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(40), dpToPx(4), Gravity.CENTER));

        header.addView(pill);
        return header;
    }

    private void addStoreWebView()
    {
        // Anonymous FrameLayout that intercepts downward drag gestures when the WebView
        // is scrolled to the top, forwarding them to the sheet's drag handler so the
        // user can pull the sheet down into Stage 3 without WebView capturing the gesture.
        FrameLayout webContainer = new FrameLayout(_activity)
        {
            private float interceptDownRawY;
            private boolean dragInitialized;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev)
            {
                switch (ev.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        interceptDownRawY = ev.getRawY();
                        dragInitialized = false;
                        return false; // let WebView see the DOWN
                    case MotionEvent.ACTION_MOVE:
                        if (_webView != null && _webView.getScrollY() == 0
                                && (ev.getRawY() - interceptDownRawY) > dpToPx(8))
                        {
                            return true; // claim the gesture — sheet will drag
                        }
                        break;
                }
                return false;
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev)
            {
                if (!dragInitialized)
                {
                    dragInitialized = true;
                    // Synthesize ACTION_DOWN at the gesture's original position so that
                    // handleSheetDrag() initialises _dragStartY / _dragStartTranslation
                    // correctly before processing the first intercepted MOVE.
                    MotionEvent syntheticDown = MotionEvent.obtain(
                            ev.getDownTime(), ev.getEventTime(),
                            MotionEvent.ACTION_DOWN,
                            ev.getRawX(), interceptDownRawY, ev.getMetaState());
                    handleSheetDrag(syntheticDown);
                    syntheticDown.recycle();
                }
                return handleSheetDrag(ev);
            }
        };

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        containerParams.topMargin = dpToPx(8);
        webContainer.setLayoutParams(containerParams);

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
        _storePageLoaded = false;
        _appTitle = null;
        _webView.setWebChromeClient(new WebChromeClient()
        {
            @Override
            public void onReceivedTitle(WebView view, String title)
            {
                if (title == null || title.isEmpty()) return;
                // Strip the " - Apps on Google Play" suffix that Play Store appends
                int dash = title.indexOf(" - ");
                _appTitle = (dash > 0) ? title.substring(0, dash).trim() : title.trim();
            }
        });
        _webView.setWebViewClient(new WebViewClient()
        {
            @Override
            public void onPageFinished(WebView view, String url)
            {
                spinner.setVisibility(View.GONE);
                // Mark the Play Store page as loaded so we can distinguish
                // user-initiated market:// taps from the automatic intent:// redirect
                // the Play Store page fires on load to try opening the native app.
                if (url != null && url.contains("play.google.com/store"))
                {
                    _storePageLoaded = true;
                    // Late-extract package ID from the resolved Play Store URL
                    // in case clickUrl was an Adjust tracker without ?id= in it.
                    if ((_targetPackage == null || _targetPackage.isEmpty()) && url.contains("id="))
                    {
                        try
                        {
                            String id = Uri.parse(url).getQueryParameter("id");
                            if (id != null && !id.isEmpty()) _targetPackage = id;
                        }
                        catch (Exception ignored) {}
                    }
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
            {
                String url = request.getUrl().toString();
                if (url.startsWith("market://") || url.startsWith("intent://"))
                {
                    // Only move to Stage 3 after the Play Store page has fully loaded.
                    // The page fires an automatic intent:// redirect on load to open the
                    // native app — we must ignore that and wait for a user-initiated tap.
                    if (_storePageLoaded)
                    {
                        _activity.runOnUiThread(() -> { if (!_isCancelled) transitionToNativeFallback(); });
                    }
                    // Always return true — WebView cannot navigate market:// or intent://.
                    return true;
                }
                // Allow all other navigations: Adjust redirect chain, Play Store pages, etc.
                return false;
            }
        });

        webContainer.addView(_webView);
        webContainer.addView(spinner);
        _sheet.addView(webContainer);
    }

    // --- Stage 3 ---

    private void transitionToNativeFallback()
    {
        if (_state == State.NATIVE_FALLBACK) return;
        _state = State.NATIVE_FALLBACK;

        destroyWebView();

        // Resume video immediately — Stage 3 plays alongside the video
        _listener.onCollapsed();

        // Slide the sheet off-screen, then show the native dialog
        animateSheet(_sheetHeight, 0f, () ->
        {
            if (_scrim != null) _scrim.setVisibility(View.INVISIBLE);
            if (!_isCancelled) showStage3Card();
        });
    }

    /**
     * Stage 3 card: full-width card (with side margins) showing icon + app title + GET button.
     * Slides in from the bottom over the video. Stays on screen until the ad close button is tapped.
     */
    private void showStage3Card()
    {
        if (_isCancelled || _activity.isFinishing()) return;

        LinearLayout card = new LinearLayout(_activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dpToPx(16));
        card.setBackground(cardBg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            card.setElevation(dpToPx(8));
        }

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cardLp.leftMargin  = dpToPx(16);
        cardLp.rightMargin = dpToPx(16);
        cardLp.bottomMargin = dpToPx(16);
        card.setLayoutParams(cardLp);

        // Icon
        if (_config != null && _config.hasValidIcon())
        {
            ImageView iconView = new ImageView(_activity);
            int iconSize = dpToPx(52);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconLp.rightMargin = dpToPx(12);
            iconView.setLayoutParams(iconLp);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Bitmap icon = BitmapFactory.decodeFile(_config.iconPath);
            if (icon != null) iconView.setImageBitmap(icon);
            card.addView(iconView);
        }

        // App title — fills remaining space between icon and GET button
        TextView nameText = new TextView(_activity);
        String displayName = (_appTitle != null && !_appTitle.isEmpty())
                ? _appTitle
                : (_targetPackage != null ? _targetPackage : "");
        nameText.setText(displayName);
        nameText.setTextColor(Color.parseColor("#212121"));
        nameText.setTextSize(14);
        nameText.setTypeface(Typeface.DEFAULT_BOLD);
        nameText.setMaxLines(2);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameLp.rightMargin = dpToPx(12);
        nameText.setLayoutParams(nameLp);
        card.addView(nameText);

        // GET button
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#4CAF50"));
        btnBg.setCornerRadius(dpToPx(6));

        TextView getBtn = new TextView(_activity);
        getBtn.setText("GET");
        getBtn.setTextColor(Color.WHITE);
        getBtn.setTextSize(14);
        getBtn.setTypeface(Typeface.DEFAULT_BOLD);
        getBtn.setGravity(Gravity.CENTER);
        getBtn.setBackground(btnBg);
        getBtn.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.gravity = Gravity.CENTER_VERTICAL;
        getBtn.setLayoutParams(btnLp);
        getBtn.setOnClickListener(v -> openStore());
        card.addView(getBtn);

        _stage3Card = card;
        _rootLayout.addView(_stage3Card);

        // Slide in from below
        _stage3Card.setTranslationY(dpToPx(200));
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

    private void openStore()
    {
        if (_targetPackage == null || _targetPackage.isEmpty()) return;
        StoreOpener.openStore(_activity, _targetPackage, null);
    }

    private void pulsateNativeFallback()
    {
        if (_stage3Card == null) return;
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(_stage3Card, "scaleX", 1f, 1.04f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(_stage3Card, "scaleY", 1f, 1.04f, 1f);
        AnimatorSet pulse = new AnimatorSet();
        pulse.playTogether(scaleX, scaleY);
        pulse.setDuration(350);
        pulse.setInterpolator(new DecelerateInterpolator());
        pulse.start();
    }

    // --- WebView Lifecycle ---

    /** Call from Activity.onPause() — suspends JS timers and media inside the WebView. */
    public void pauseWebContent()
    {
        if (_webView != null) _webView.onPause();
    }

    /** Call from Activity.onResume() — resumes the WebView when Stage 2 is still open. */
    public void resumeWebContent()
    {
        if (_webView != null) _webView.onResume();
    }

    private void destroyWebView()
    {
        if (_webView == null) return;
        ViewGroup parent = (ViewGroup) _webView.getParent();
        if (parent != null) parent.removeView(_webView); // detach before destroy
        _webView.destroy();
        _webView = null;
    }

    // --- Drag Handling ---

    private void setupDragListener()
    {
        _sheet.setOnTouchListener((v, event) -> handleSheetDrag(event));
    }

    /**
     * Core sheet drag logic. Called from both the sheet's own OnTouchListener and from
     * the WebView container intercept path, so the same gesture handling applies
     * whether the touch originates on the sheet background or within the WebView area.
     */
    private boolean handleSheetDrag(MotionEvent event)
    {
        if (_state == State.HIDDEN || _state == State.NATIVE_FALLBACK) return false;

        switch (event.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                _dragStartY = event.getRawY();
                _dragStartTranslation = _sheet.getTranslationY();
                return true;

            case MotionEvent.ACTION_MOVE:
                float delta = event.getRawY() - _dragStartY;
                float newTranslation = _dragStartTranslation + delta;
                // Clamp between fully expanded (0) and fully off-screen (_sheetHeight)
                newTranslation = Math.max(0f, Math.min(newTranslation, (float) _sheetHeight));
                _sheet.setTranslationY(newTranslation);
                updateScrimAlpha(newTranslation);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                snapSheet(_sheet.getTranslationY());
                return true;
        }
        return false;
    }

    private void updateScrimAlpha(float translationY)
    {
        if (_sheetHeight <= 0) return;
        float progress = 1f - (translationY / _sheetHeight);
        progress = Math.max(0f, Math.min(1f, progress));
        _scrim.setAlpha(progress * SCRIM_MAX_ALPHA);
    }

    private void snapSheet(float currentTranslation)
    {
        if (_state != State.EXPANDED) return;
        // Snap to Stage 3 if dragged more than 40% down, otherwise spring back open
        if (currentTranslation > _sheetHeight * 0.4f)
        {
            transitionToNativeFallback();
        }
        else
        {
            animateSheet(0f, SCRIM_MAX_ALPHA, null);
        }
    }

    // --- State Transitions ---

    private void peek()
    {
        _state = State.PEEK;
        // Start below the screen so the card slides in from the bottom-right.
        // Use post() so getHeight() is valid after the first layout pass.
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

    private String extractTargetPackage(String clickUrl)
    {
        if (clickUrl == null || clickUrl.isEmpty()) return null;
        try { return Uri.parse(clickUrl).getQueryParameter("id"); }
        catch (Exception e) { return null; }
    }

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
