package com.ua.toolkit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.util.TypedValue;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
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
        void onInstallClicked();
    }

    private final Activity activity;
    private final Listener listener;
    private final boolean isRewarded;

    // Views
    private FrameLayout rootLayout;
    private VideoView videoView;
    private Button muteButton;
    private TextView timerText;
    private TextView countdownText;
    private TextView closeButton;
    private TextView installButton;
    private FrameLayout buttonContainer;

    // Insets
    private int savedTopInset = 0;
    private int savedLeftInset = 0;
    private int savedRightInset = 0;
    private int savedBottomInset = 0;
    private boolean insetsApplied = false;

    public AdUIManager(Activity activity, Listener listener, boolean isRewarded)
    {
        this.activity = activity;
        this.listener = listener;
        this.isRewarded = isRewarded;
    }

    @SuppressLint("ClickableViewAccessibility")
    public void setupUI()
    {
        rootLayout = new FrameLayout(activity);
        rootLayout.setBackgroundColor(Color.BLACK);

        createVideoView();
        createMuteButton();
        createTimerText();
        createButtonContainer();
        createInstallButton();
        setupInsetHandling();

        rootLayout.addView(videoView);
        rootLayout.addView(timerText);
        rootLayout.addView(muteButton);
        rootLayout.addView(buttonContainer);
        rootLayout.addView(installButton);

        activity.setContentView(rootLayout);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createVideoView()
    {
        videoView = new VideoView(activity);
        videoView.setLayoutParams(new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
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
        muteButton.setTextSize(18);

        GradientDrawable bg = createCircleBackground();
        muteButton.setBackground(bg);
        clearBackgroundTint(muteButton);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dpToPx(40), dpToPx(40), Gravity.TOP | Gravity.START);
        params.topMargin = dpToPx(8);
        params.leftMargin = dpToPx(14);
        muteButton.setLayoutParams(params);
        muteButton.setOnClickListener(v -> listener.onMuteClicked());
    }

    private void createTimerText()
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#80000000"));
        bg.setCornerRadius(dpToPx(33));

        timerText = new TextView(activity);
        timerText.setTextColor(Color.WHITE);
        timerText.setTextSize(16);
        timerText.setPadding(dpToPx(17), dpToPx(8), dpToPx(17), dpToPx(8));
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
        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(dpToPx(24), dpToPx(24), Gravity.TOP | Gravity.END);
        containerParams.topMargin = dpToPx(8);
        containerParams.rightMargin = dpToPx(14);
        buttonContainer.setLayoutParams(containerParams);

        // Countdown text (same size as close button)
        countdownText = new TextView(activity);
        countdownText.setTextColor(Color.argb(150, 255, 255, 255));
        countdownText.setTextSize(10);
        countdownText.setGravity(Gravity.CENTER);
        countdownText.setIncludeFontPadding(false);
        countdownText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        countdownText.setBackground(createTransparentCircleBackground());
        countdownText.setPadding(0, 0, 0, 0);
        countdownText.setMinHeight(0);
        countdownText.setMinimumHeight(0);
        countdownText.setMinWidth(0);
        countdownText.setMinimumWidth(0);
        countdownText.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(20), dpToPx(20), Gravity.CENTER));
        countdownText.setVisibility(isRewarded ? View.GONE : View.VISIBLE);

        // Close button (using TextView to avoid Button's internal padding/min size)
        closeButton = new TextView(activity);
        closeButton.setText("✕");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(10);
        closeButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setIncludeFontPadding(false);
        closeButton.setVisibility(View.GONE);
        closeButton.setBackground(createCircleBackground());
        closeButton.setPadding(0, 0, 0, 0);
        closeButton.setMinHeight(0);
        closeButton.setMinimumHeight(0);
        closeButton.setMinWidth(0);
        closeButton.setMinimumWidth(0);
        closeButton.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(20), dpToPx(20), Gravity.CENTER));
        closeButton.setOnClickListener(v -> listener.onCloseClicked());

        buttonContainer.addView(countdownText);
        buttonContainer.addView(closeButton);
    }

    private void createInstallButton()
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#4CAF50"));
        bg.setCornerRadius(dpToPx(4));

        installButton = new TextView(activity);
        installButton.setText("INSTALL");
        installButton.setTextColor(Color.WHITE);
        installButton.setTextSize(12);
        installButton.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        installButton.setGravity(Gravity.CENTER);
        installButton.setIncludeFontPadding(false);
        installButton.setBackground(bg);
        installButton.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        params.bottomMargin = dpToPx(14);
        params.rightMargin = dpToPx(14);
        installButton.setLayoutParams(params);
        installButton.setOnClickListener(v -> listener.onInstallClicked());
    }

    private void setupInsetHandling()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
            rootLayout.setOnApplyWindowInsetsListener((v, insets) ->
            {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());

                savedTopInset = bars.top;
                savedLeftInset = bars.left;
                savedRightInset = bars.right;
                savedBottomInset = bars.bottom;

                applyInsets();
                return insets;
            });
        }
    }

    public void applyInsets()
    {
        if (savedTopInset <= 0 && !insetsApplied) return;
        insetsApplied = true;

        // Mute button
        FrameLayout.LayoutParams lpM = (FrameLayout.LayoutParams) muteButton.getLayoutParams();
        lpM.topMargin = savedTopInset + 20;
        lpM.leftMargin = savedLeftInset + 40;
        muteButton.setLayoutParams(lpM);

        // Timer text
        FrameLayout.LayoutParams lpT = (FrameLayout.LayoutParams) timerText.getLayoutParams();
        lpT.topMargin = savedTopInset + 20;
        timerText.setLayoutParams(lpT);

        // Button container
        FrameLayout.LayoutParams lpC = (FrameLayout.LayoutParams) buttonContainer.getLayoutParams();
        lpC.topMargin = savedTopInset + 20;
        lpC.rightMargin = savedRightInset + 40;
        buttonContainer.setLayoutParams(lpC);

        // Install button
        FrameLayout.LayoutParams lpI = (FrameLayout.LayoutParams) installButton.getLayoutParams();
        lpI.bottomMargin = savedBottomInset + dpToPx(14);
        lpI.rightMargin = savedRightInset + dpToPx(14);
        installButton.setLayoutParams(lpI);
    }

    public void setupFullscreen()
    {
        if (activity.getWindow() == null) return;
        View decorView = activity.getWindow().getDecorView();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        {
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
    }

    // --- UI Update Methods ---

    @SuppressLint("SetTextI18n")
    public void updateCountdown(int seconds)
    {
        if (countdownText != null)
        {
            countdownText.setText(String.valueOf(seconds));
        }
    }

    @SuppressLint("SetTextI18n")
    public void updateRewardTimer(int seconds)
    {
        if (timerText != null)
        {
            timerText.setText("Reward in: " + seconds + "s");
        }
    }

    public void showRewardEarned()
    {
        if (timerText != null)
        {
            timerText.setText("✓ Reward earned!");
        }
    }

    public void showCloseButton()
    {
        if (countdownText != null) countdownText.setVisibility(View.GONE);
        if (closeButton != null) closeButton.setVisibility(View.VISIBLE);
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

    // --- Helpers ---

    private GradientDrawable createCircleBackground()
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#AA000000"));
        bg.setStroke(dpToPx(1.5f), Color.WHITE);
        return bg;
    }

    private GradientDrawable createTransparentCircleBackground()
    {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(80, 0, 0, 0));
        bg.setStroke(dpToPx(1.5f), Color.argb(150, 255, 255, 255));
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