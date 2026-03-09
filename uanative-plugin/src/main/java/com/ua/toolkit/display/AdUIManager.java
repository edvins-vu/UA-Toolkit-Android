package com.ua.toolkit.display;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.util.TypedValue;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;

/**
 * Manages UI creation and updates for ad display.
 */
public class AdUIManager
{
    public interface Listener
    {
        void onCloseClicked();
        void onMuteClicked();
        void onVideoTouched();
    }

    /** Fired once, after the first valid inset dispatch — AdPopup uses this to position cards. */
    public interface InsetsReadyCallback
    {
        void onInsetsReady(int bottomInset, int rightInset);
    }

    private final Activity activity;
    private final Listener listener;
    private final boolean isRewarded;
    private String rewardCountdownText = "Reward in: %ds";
    private String rewardEarnedText    = "Reward earned!";

    // Views
    private FrameLayout rootLayout;
    private VideoView videoView;
    private Button muteButton;
    private TextView timerText;
    private TextView skipButton;
    private TextView closeButton;
    private FrameLayout buttonContainer;

    // Insets
    private int savedTopInset = 0;
    private int savedLeftInset = 0;
    private int savedRightInset = 0;
    private int savedBottomInset = 0;
    private boolean insetsApplied = false; // guard: only apply once — overlays cause transient re-dispatches
    private InsetsReadyCallback insetsReadyCallback;

    public AdUIManager(Activity activity, Listener listener, boolean isRewarded,
                       String rewardCountdownText, String rewardEarnedText)
    {
        this.activity = activity;
        this.listener = listener;
        this.isRewarded = isRewarded;
        if (rewardCountdownText != null && !rewardCountdownText.isEmpty())
            this.rewardCountdownText = rewardCountdownText;
        if (rewardEarnedText != null && !rewardEarnedText.isEmpty())
            this.rewardEarnedText = rewardEarnedText;
    }

    public void setInsetsReadyCallback(InsetsReadyCallback cb)
    {
        insetsReadyCallback = cb;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void setupUI()
    {
        Window window = activity.getWindow();

        // 1. Modern Edge-to-Edge Setup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }

        // 2. Cutout and Bar Transparency (Essential for Xiaomi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }

        // FLAG_LAYOUT_NO_LIMITS bypasses MIUI/HyperOS safe-area enforcement at the WindowManager
        // level, ensuring the window extends to physical screen edges past the display cutout.
        window.addFlags(
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        rootLayout = new FrameLayout(activity);
        rootLayout.setBackgroundColor(Color.BLACK);

        createVideoView();
        createMuteButton();
        createTimerText();
        createButtonContainer();
        setupInsetHandling();

        rootLayout.addView(videoView);
        rootLayout.addView(timerText);
        rootLayout.addView(muteButton);
        rootLayout.addView(buttonContainer);

        activity.setContentView(rootLayout);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createVideoView()
    {
        // FillVideoView overrides onMeasure to skip VideoView's built-in aspect-ratio
        // correction, ensuring the surface fills the screen on any device ratio (16:9, 20:9, etc.)
        videoView = new FillVideoView(activity);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        videoView.setLayoutParams(params);

        videoView.setOnTouchListener((v, event) ->
        {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP)
            {
                listener.onVideoTouched();
            }
            return true;
        });
    }

    private void createMuteButton()
    {
        muteButton = new Button(activity);
        muteButton.setText("🔊");
        muteButton.setTextColor(Color.WHITE);
        muteButton.setTextSize(14);
        muteButton.setIncludeFontPadding(false);
        muteButton.setMinHeight(0);
        muteButton.setMinimumHeight(0);
        muteButton.setMinWidth(0);
        muteButton.setMinimumWidth(0);
        muteButton.setPadding(0, 0, 0, 0);
        muteButton.setGravity(Gravity.CENTER);

        muteButton.setBackground(createRoundedBackground(dpToPx(8)));
        clearBackgroundTint(muteButton);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dpToPx(28), dpToPx(28), Gravity.TOP | Gravity.START);
        params.topMargin = dpToPx(8);
        params.leftMargin = dpToPx(20);
        muteButton.setLayoutParams(params);
        muteButton.setOnClickListener(v -> listener.onMuteClicked());
    }

    private void createTimerText()
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#80000000"));
        bg.setCornerRadius(dpToPx(23));

        timerText = new TextView(activity);
        timerText.setTextColor(Color.WHITE);
        timerText.setTextSize(11);
        timerText.setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6));
        timerText.setBackground(bg);
        timerText.setGravity(Gravity.CENTER);
        timerText.setVisibility(isRewarded ? View.VISIBLE : View.GONE);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = dpToPx(8);
        timerText.setLayoutParams(params);
    }

    private void createButtonContainer()
    {
        buttonContainer = new FrameLayout(activity);
        // WRAP_CONTENT so the container expands to accommodate inset padding applied in applyInsets()
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dpToPx(28), Gravity.TOP | Gravity.END);
        containerParams.topMargin = dpToPx(8);
        containerParams.rightMargin = dpToPx(28); // minimal initial value; zeroed in applyInsets()
        buttonContainer.setLayoutParams(containerParams);

        // Skip button — shown after 3 s; tap immediately reveals close button
        skipButton = new TextView(activity);
        skipButton.setText("⏭");
        skipButton.setTextColor(Color.argb(150, 255, 255, 255));
        skipButton.setTextSize(15);
        skipButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        skipButton.setGravity(Gravity.CENTER);
        skipButton.setIncludeFontPadding(false);
        skipButton.setBackground(createRoundedBackground(dpToPx(8)));
        skipButton.setPadding(0, 0, 0, 0);
        skipButton.setMinHeight(0);
        skipButton.setMinimumHeight(0);
        skipButton.setMinWidth(0);
        skipButton.setMinimumWidth(0);
        // Rectangular — wider than tall to match iOS skip button shape
        skipButton.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(56), dpToPx(28), Gravity.CENTER));
        skipButton.setVisibility(View.GONE);
        skipButton.setOnClickListener(v -> showCloseButton());

        // Close button (using TextView to avoid Button's internal padding/min size)
        closeButton = new TextView(activity);
        closeButton.setText("✕");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(10);
        closeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setIncludeFontPadding(false);
        closeButton.setVisibility(View.GONE);
        closeButton.setBackground(createRoundedBackground(dpToPx(8)));
        closeButton.setPadding(0, 0, 0, 0);
        closeButton.setMinHeight(0);
        closeButton.setMinimumHeight(0);
        closeButton.setMinWidth(0);
        closeButton.setMinimumWidth(0);
        closeButton.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(20), dpToPx(20), Gravity.CENTER));
        closeButton.setOnClickListener(v -> listener.onCloseClicked());

        buttonContainer.addView(skipButton);
        buttonContainer.addView(closeButton);
    }

    private void setupInsetHandling()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            // API 30+: use the typed insets API which combines systemBars + displayCutout
            rootLayout.setOnApplyWindowInsetsListener((v, insets) ->
            {
                // Values AND apply are both guarded — overlay re-dispatches (e.g. Play Store
                // half-sheet) must not overwrite the frozen inset values or re-position buttons.
                if (!insetsApplied)
                {
                    android.graphics.Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    savedTopInset   = bars.top;
                    savedLeftInset  = bars.left;
                    savedRightInset = bars.right;
                    // FLAG_LAYOUT_NO_LIMITS causes systemBars() bottom to report 0 even when a
                    // physical nav bar exists. getInsetsIgnoringVisibility gives the real height.
                    android.graphics.Insets nav = insets.getInsetsIgnoringVisibility(
                            WindowInsets.Type.navigationBars());
                    savedBottomInset = Math.max(bars.bottom, nav.bottom);
                    insetsApplied = true;
                    applyInsets();
                }
                return insets;
            });
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            // API 28–29: DisplayCutout was added in API 28 but the typed API requires API 30.
            // Read cutout safe insets directly; fall back to system window insets if no cutout.
            rootLayout.setOnApplyWindowInsetsListener((v, insets) ->
            {
                if (!insetsApplied)
                {
                    android.view.DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null)
                    {
                        savedTopInset    = cutout.getSafeInsetTop();
                        savedLeftInset   = cutout.getSafeInsetLeft();
                        savedRightInset  = cutout.getSafeInsetRight();
                        savedBottomInset = cutout.getSafeInsetBottom();
                    }
                    else
                    {
                        savedTopInset    = insets.getSystemWindowInsetTop();
                        savedLeftInset   = insets.getSystemWindowInsetLeft();
                        savedRightInset  = insets.getSystemWindowInsetRight();
                        savedBottomInset = insets.getSystemWindowInsetBottom();
                    }
                    insetsApplied = true;
                    applyInsets();
                }
                return insets;
            });
        }
    }

    public void applyInsets()
    {
        int top  = Math.max(savedTopInset,  dpToPx(8));
        int left = savedLeftInset + dpToPx(20);

        // Mute button — offset from left edge / notch via margin
        FrameLayout.LayoutParams lpM = (FrameLayout.LayoutParams) muteButton.getLayoutParams();
        lpM.topMargin  = top;
        lpM.leftMargin = left;
        muteButton.setLayoutParams(lpM);

        // Timer text — stay centred but below notch / status bar
        FrameLayout.LayoutParams lpT = (FrameLayout.LayoutParams) timerText.getLayoutParams();
        lpT.topMargin = top;
        timerText.setLayoutParams(lpT);

        // Button container — sits flush at the right screen edge (rightMargin = 0).
        // Right inset is applied as padding inside the container so the close/skip buttons
        // are offset from the notch without creating any external margin or black bar.
        FrameLayout.LayoutParams lpC = (FrameLayout.LayoutParams) buttonContainer.getLayoutParams();
        lpC.topMargin   = top;
        lpC.rightMargin = 0;
        buttonContainer.setLayoutParams(lpC);
        buttonContainer.setPadding(0, 0, savedRightInset + dpToPx(28), 0);

        // Notify popup so it can position its cards above the navigation bar
        if (insetsReadyCallback != null)
            insetsReadyCallback.onInsetsReady(savedBottomInset, savedRightInset);
    }

    public void setupFullscreen()
    {
        if (activity.getWindow() == null) return;
        View decorView = activity.getWindow().getDecorView();

        // Re-apply cutout mode on every call — MIUI/HyperOS resets window attributes on resume.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            activity.getWindow().setAttributes(lp);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            // Re-apply edge-to-edge and MIUI bypass flags — HyperOS may reset these on resume.
            activity.getWindow().setDecorFitsSystemWindows(false);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null)
            {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        else
        {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN);
        }

        // Force re-dispatch of display-cutout insets after fullscreen flags settle.
        if (rootLayout != null)
        {
            rootLayout.requestApplyInsets();
        }
    }

    // --- UI Update Methods ---

    private int lastRewardTimerValue = -1;

    @SuppressLint("SetTextI18n")
    public void updateRewardTimer(int seconds)
    {
        if (timerText != null && seconds != lastRewardTimerValue)
        {
            lastRewardTimerValue = seconds;
            timerText.setText(String.format(rewardCountdownText, seconds));
        }
    }

    public void showRewardEarned()
    {
        if (timerText != null)
        {
            timerText.setText(rewardEarnedText);
        }
    }

    public void showSkipButton()
    {
        if (skipButton == null || skipButton.getVisibility() == View.VISIBLE) return;
        skipButton.setVisibility(View.VISIBLE);
    }

    public void showCloseButton()
    {
        if (skipButton != null) skipButton.setVisibility(View.GONE);
        if (closeButton != null) closeButton.setVisibility(View.VISIBLE);
    }

    public void hideCloseButton()
    {
        if (closeButton != null) closeButton.setVisibility(View.GONE);
    }

    public void updateMuteButton(boolean isMuted)
    {
        if (muteButton != null)
        {
            muteButton.setText(isMuted ? "🔇" : "🔊");
        }
    }

    public boolean isCloseButtonVisible()
    {
        return closeButton != null && closeButton.getVisibility() == View.VISIBLE;
    }

    // --- Getters ---

    public VideoView getVideoView()
    {
        return videoView;
    }

    public FrameLayout getRootLayout()
    {
        return rootLayout;
    }

    // --- Helpers ---

    /**
     * Rounded-rectangle background matching iOS UAAdViewController button style:
     *   backgroundColor = colorWithWhite:0.15 alpha:0.75  →  argb(191, 38, 38, 38)
     *   borderWidth = 0.5 / borderColor = white alpha:0.2 →  argb(51, 255, 255, 255)
     * Applied consistently to mute, skip, and close buttons for visual parity with iOS.
     */
    private GradientDrawable createRoundedBackground(int cornerRadius)
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(cornerRadius);
        bg.setColor(Color.argb(191, 38, 38, 38));
        bg.setStroke(dpToPx(0.75f), Color.argb(51, 255, 255, 255));
        return bg;
    }

    private void clearBackgroundTint(View view)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && view instanceof Button)
        {
            ((Button) view).setBackgroundTintList(null);
        }
    }

    private int dpToPx(float dp)
    {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp,
            activity.getResources().getDisplayMetrics());
    }
}