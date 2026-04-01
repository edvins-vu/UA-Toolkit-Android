package com.ua.toolkit.display;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.VideoView;

import com.ua.toolkit.R;
import com.ua.toolkit.popup.AdVisualsHelper;

/**
 * Manages UI creation and updates for ad display.
 */
public class AdUIManager
{
    public enum CornerButtonState { OPEN_STORE, CLOSE }

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
    private String  rewardCountdownText  = "Reward in: %ds";
    private String  rewardEarnedText     = "Reward earned!";
    private boolean disableRewardCountdown = false;
    private int     rewardTextSizeSp     = 14;
    private String  rewardTextColor      = "#FFFFFF";

    // Views
    private FrameLayout rootLayout;
    private VideoView videoView;
    private ImageButton muteButton;
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

    private boolean      disableMuteButton  = false;
    private boolean      disableSkipButton  = false;
    private final AdUILayout _layout = new AdUILayout();

    // Flow B
    private boolean           isFlowB           = false;
    private CornerButtonState cornerButtonState = CornerButtonState.OPEN_STORE;

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

    public void setDisableMuteButton(boolean disable)        { this.disableMuteButton = disable; }
    public void setDisableSkipButton(boolean disable)        { this.disableSkipButton = disable; }
    public void setDisableRewardCountdown(boolean disable)   { this.disableRewardCountdown = disable; }
    public void setRewardTextSizeSp(int sp)                  { this.rewardTextSizeSp = sp; }
    public void setRewardTextColor(String hex)               { this.rewardTextColor = hex; }
    public void setFlowB(boolean flowB)                      { this.isFlowB = flowB; }

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

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
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
        muteButton = new ImageButton(activity);
        muteButton.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int pad = dpToPx(_layout.muteButtonPaddingDp);
        muteButton.setPadding(pad, pad, pad, pad);
        muteButton.setImageResource(R.drawable.ic_volume_on);

        muteButton.setBackground(AdVisualsHelper.makeCircleBackground(
                _layout.buttonBgColor, _layout.buttonBorderColor, dpToPx(_layout.buttonBorderWidthDp)));
        clearBackgroundTint(muteButton);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dpToPx(_layout.muteButtonSizeDp), dpToPx(_layout.muteButtonSizeDp), Gravity.TOP | Gravity.START);
        params.topMargin = dpToPx(_layout.muteButtonTopMarginDp);
        params.leftMargin = dpToPx(_layout.muteButtonLeftMarginDp);
        muteButton.setLayoutParams(params);
        muteButton.setVisibility(disableMuteButton ? View.GONE : View.VISIBLE);
        muteButton.setOnClickListener(v -> listener.onMuteClicked());
    }

    private void updateMuteButtonIcon(boolean isMuted)
    {
        muteButton.setImageResource(isMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_on);
    }

    private void createTimerText()
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(AdVisualsHelper.parseColor(_layout.timerBackgroundColor, Color.BLACK));
        bg.setCornerRadius(dpToPx(_layout.timerCornerRadiusDp));

        timerText = new TextView(activity);
        timerText.setTextColor(AdVisualsHelper.parseColor(rewardTextColor, Color.WHITE));
        timerText.setTextSize(rewardTextSizeSp);
        timerText.setPadding(dpToPx(_layout.timerPaddingHorizontalDp), dpToPx(_layout.timerPaddingVerticalDp),
                dpToPx(_layout.timerPaddingHorizontalDp), dpToPx(_layout.timerPaddingVerticalDp));
        timerText.setBackground(bg);
        timerText.setGravity(Gravity.CENTER);
        timerText.setVisibility(isRewarded && !disableRewardCountdown ? View.VISIBLE : View.GONE);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = dpToPx(_layout.timerTopMarginDp);
        timerText.setLayoutParams(params);
    }

    private void createButtonContainer()
    {
        buttonContainer = new FrameLayout(activity);
        // WRAP_CONTENT so the container expands to accommodate inset padding applied in applyInsets()
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dpToPx(_layout.buttonContainerHeightDp), Gravity.TOP | Gravity.END);
        containerParams.topMargin = dpToPx(_layout.buttonContainerTopMarginDp);
        containerParams.rightMargin = dpToPx(_layout.buttonContainerRightDp); // minimal initial value; zeroed in applyInsets()
        buttonContainer.setLayoutParams(containerParams);

        // Skip button — shown after 3 s; tap immediately reveals close button
        skipButton = new TextView(activity);
        skipButton.setText("⏭");
        skipButton.setTextColor(_layout.skipButtonTextColor);
        skipButton.setTextSize(_layout.skipButtonTextSizeSp);
        skipButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        skipButton.setGravity(Gravity.CENTER);
        skipButton.setIncludeFontPadding(false);
        skipButton.setBackground(null); // icon only — no background or ripple
        skipButton.setPadding(0, 0, 0, 0);
        skipButton.setMinHeight(0);
        skipButton.setMinimumHeight(0);
        skipButton.setMinWidth(0);
        skipButton.setMinimumWidth(0);
        skipButton.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(_layout.skipButtonSizeDp), dpToPx(_layout.skipButtonSizeDp), Gravity.CENTER));
        skipButton.setVisibility(View.GONE);
        skipButton.setOnClickListener(v -> showCloseButton());

        // Close button (using TextView to avoid Button's internal padding/min size)
        closeButton = new TextView(activity);
        closeButton.setText("✕");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(_layout.closeButtonTextSizeSp);
        closeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setIncludeFontPadding(false);
        closeButton.setVisibility(View.GONE);
        closeButton.setBackground(AdVisualsHelper.makeRoundedBackground(
                _layout.buttonBgColor, _layout.buttonBorderColor,
                dpToPx(_layout.closeButtonCornerRadiusDp), dpToPx(_layout.buttonBorderWidthDp)));
        closeButton.setPadding(0, 0, 0, 0);
        closeButton.setMinHeight(0);
        closeButton.setMinimumHeight(0);
        closeButton.setMinWidth(0);
        closeButton.setMinimumWidth(0);
        closeButton.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(_layout.closeButtonSizeDp), dpToPx(_layout.closeButtonSizeDp), Gravity.CENTER));
        closeButton.setOnClickListener(v -> listener.onCloseClicked());

        if (isFlowB)
        {
            // Corner button starts as OPEN_STORE — visible immediately, skip button suppressed
            closeButton.setText("OPEN STORE");
            closeButton.setVisibility(View.VISIBLE);
            skipButton.setVisibility(View.GONE);
        }

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
        int top  = Math.max(savedTopInset,  dpToPx(_layout.minTopInsetDp));
        int left = savedLeftInset + dpToPx(_layout.muteButtonLeftMarginDp);

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
        buttonContainer.setPadding(0, 0, savedRightInset + dpToPx(_layout.buttonContainerRightDp), 0);

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
            // Re-apply edge-to-edge on resume — MIUI/HyperOS may reset this.
            activity.getWindow().setDecorFitsSystemWindows(false);

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
            timerText.setVisibility(View.VISIBLE); // always show earned text even if countdown was hidden
        }
    }

    public void showSkipButton()
    {
        if (isFlowB) return;
        if (disableSkipButton) return;
        if (skipButton == null || skipButton.getVisibility() == View.VISIBLE) return;
        skipButton.setVisibility(View.VISIBLE);
    }

    public void showCloseButton()
    {
        if (isFlowB) return;
        if (skipButton != null) {
            skipButton.setPressed(false); // clear pressed/ripple state before hiding
            skipButton.setVisibility(View.INVISIBLE);
        }
        if (closeButton != null) closeButton.setVisibility(View.VISIBLE);
    }

    public void hideCloseButton()
    {
        if (closeButton != null) closeButton.setVisibility(View.GONE);
    }

    public void updateMuteButton(boolean isMuted)
    {
        if (muteButton != null) updateMuteButtonIcon(isMuted);
    }

    public boolean isCloseButtonVisible()
    {
        return closeButton != null && closeButton.getVisibility() == View.VISIBLE;
    }

    // --- Flow B ---

    public void setCornerButtonState(CornerButtonState state)
    {
        if (!isFlowB || closeButton == null) return;
        cornerButtonState = state;
        switch (state)
        {
            case OPEN_STORE:
                closeButton.setText("OPEN STORE");
                closeButton.setEnabled(true);
                closeButton.setAlpha(1.0f);
                closeButton.setVisibility(View.VISIBLE);
                break;
            case CLOSE:
                closeButton.setText("✕");
                closeButton.setEnabled(true);
                closeButton.setAlpha(1.0f);
                closeButton.setVisibility(View.VISIBLE);
                break;
        }
    }

    public CornerButtonState getCornerButtonState() { return cornerButtonState; }

    public void transitionCornerButtonToClose(int fadeDurationMs)
    {
        if (!isFlowB || closeButton == null) return;
        closeButton.animate()
            .alpha(0f)
            .setDuration(fadeDurationMs)
            .withEndAction(() -> {
                setCornerButtonState(CornerButtonState.CLOSE);
                closeButton.animate()
                    .alpha(1f)
                    .setDuration(fadeDurationMs)
                    .start();
            })
            .start();
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