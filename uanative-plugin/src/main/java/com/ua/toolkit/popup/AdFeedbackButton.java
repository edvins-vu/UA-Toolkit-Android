package com.ua.toolkit.popup;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Self-contained feedback toggle widget shown in the bottom-left corner during Stage 1.
 *
 * Visual states:
 *   COLLAPSED  — (i) toggle button visible, panel hidden
 *   EXPANDED   — (x) toggle, panel shows "Interested" / "Not Interested" buttons
 *   EXPANDED + feedbackGiven — (x) toggle, panel shows "Thank you for your feedback!"
 *
 * After either feedback button is tapped the panel permanently shows the thank-you message;
 * the buttons are never shown again even if the user reopens the panel.
 */
class AdFeedbackButton
{
    interface Listener
    {
        void onNotInterested();
    }

    private static final int ANIM_DURATION_MS   = 150;

    // Visual constants — match AdUILayout shared button style
    private static final int   TOGGLE_SIZE_DP    = 20;
    private static final int   TOGGLE_CORNER_DP  = 8;
    private static final float BORDER_WIDTH_DP   = 0.75f;
    private static final int   BUTTON_BG_COLOR   = Color.argb(191, 38, 38, 38);
    private static final int   BORDER_COLOR      = Color.argb(51,  255, 255, 255);
    private static final int   PANEL_BG_COLOR    = Color.parseColor("#80000000");
    private static final int   PANEL_CORNER_DP   = 16;
    private static final int   PANEL_PADDING_H_DP = 14;
    private static final int   PANEL_PADDING_V_DP = 8;
    private static final int   PANEL_SPACING_DP  = 4;   // gap between panel and toggle
    private static final int   DIVIDER_COLOR     = Color.argb(80, 255, 255, 255);
    private static final int   ITEM_TEXT_SIZE_SP = 12;
    private static final int   ITEM_PADDING_V_DP = 6;
    private static final int   EDGE_MARGIN_DP    = 24;  // matches AdPopupLayout.cardEdgeMarginDp

    // Text labels
    private static final String TEXT_TOGGLE_COLLAPSED = "i";
    private static final String TEXT_TOGGLE_EXPANDED  = "✕";
    private static final String TEXT_INTERESTED       = "Interested";
    private static final String TEXT_NOT_INTERESTED   = "Not Interested";
    private static final String TEXT_THANK_YOU        = "Thank you for your feedback!";

    private final Activity _activity;
    private FrameLayout _rootLayout;
    private Listener _listener;

    // Views
    private LinearLayout _outerContainer; // vertical root anchored bottom-left
    private LinearLayout _panel;          // pill, GONE by default
    private TextView     _toggleButton;   // (i) / (✕)

    // State
    private boolean _isOpen        = false;
    private boolean _feedbackGiven = false;

    private static final int PANEL_WIDTH_DP = 140;

    AdFeedbackButton(Activity activity)
    {
        _activity = activity;
    }

    // --- Public API ---

    void attach(FrameLayout rootLayout, Listener listener)
    {
        _rootLayout = rootLayout;
        _listener   = listener;

        buildWidget();

        // Insert at index 1 (above video surface, same as stage1 card) — UIManager controls are on top
        _rootLayout.addView(_outerContainer, 1);
        _outerContainer.setVisibility(View.GONE);
        _outerContainer.setAlpha(0f);
    }

    void applyInsets(int bottomInset)
    {
        if (_outerContainer == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) _outerContainer.getLayoutParams();
        lp.bottomMargin = bottomInset + dpToPx(EDGE_MARGIN_DP);
        _outerContainer.setLayoutParams(lp);
    }

    /**
     * Restores feedback-given state after activity recreation.
     * Called before the widget is shown — views don't exist yet so no visual update is needed.
     * The next populatePanel() call will show "Thank you" instead of the option buttons.
     */
    void restoreFeedbackGiven()
    {
        _feedbackGiven = true;
    }

    /**
     * Raises the feedback widget above the Stage 1 GET card in portrait mode.
     * Called from AdPopup.peek() once the card is measured, so cardHeightPx reflects the
     * real layout height including padding and any AdConfig-driven button size changes.
     * No-op in landscape — horizontal separation is sufficient there.
     */
    void updateBottomForCard(int cardBottomMarginPx, int cardHeightPx)
    {
        if (_outerContainer == null) return;
        boolean isPortrait = _activity.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        if (!isPortrait) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) _outerContainer.getLayoutParams();
        lp.bottomMargin = cardBottomMarginPx + cardHeightPx + dpToPx(PANEL_SPACING_DP);
        _outerContainer.setLayoutParams(lp);
    }

    /** Fades the (i) toggle into view. Called from AdPopup.peek() and after store return. */
    void show()
    {
        if (_outerContainer == null) return;
        _outerContainer.animate().cancel();
        _outerContainer.setVisibility(View.VISIBLE);
        _outerContainer.animate()
                .alpha(1f)
                .setDuration(ANIM_DURATION_MS)
                .start();
    }

    /**
     * Fades the widget out without removing it from the hierarchy.
     * Resets to collapsed state (panel closed, toggle back to "i") so show() restores
     * a clean (i) toggle regardless of what state the panel was in when store was opened.
     * Called when the store opens — show() will restore the button on return.
     */
    void hide()
    {
        if (_outerContainer == null) return;
        _outerContainer.animate().cancel();
        _panel.animate().cancel();
        _isOpen = false;
        if (_toggleButton != null) _toggleButton.setText(TEXT_TOGGLE_COLLAPSED);
        _panel.setVisibility(View.GONE);
        _panel.setAlpha(0f);
        _outerContainer.animate()
                .alpha(0f)
                .setDuration(ANIM_DURATION_MS)
                .withEndAction(() ->
                {
                    if (_outerContainer != null)
                        _outerContainer.setVisibility(View.INVISIBLE);
                })
                .start();
    }

    /** Instantly removes the widget. Called from AdPopup.cancel(). */
    void cancel()
    {
        if (_outerContainer != null && _outerContainer.getParent() != null)
            _rootLayout.removeView(_outerContainer);
        _outerContainer = null;
    }

    // --- View construction ---

    private void buildWidget()
    {
        _outerContainer = new LinearLayout(_activity);
        _outerContainer.setOrientation(LinearLayout.VERTICAL);
        _outerContainer.setGravity(Gravity.START);

        FrameLayout.LayoutParams outerLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        outerLp.leftMargin   = dpToPx(EDGE_MARGIN_DP);
        outerLp.bottomMargin = dpToPx(EDGE_MARGIN_DP);
        _outerContainer.setLayoutParams(outerLp);

        _panel = buildPanel();
        _outerContainer.addView(_panel);

        _toggleButton = buildToggleButton();
        _outerContainer.addView(_toggleButton);
    }

    private LinearLayout buildPanel()
    {
        LinearLayout panel = new LinearLayout(_activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);

        int padH = dpToPx(PANEL_PADDING_H_DP);
        int padV = dpToPx(PANEL_PADDING_V_DP);
        panel.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL_BG_COLOR);
        bg.setCornerRadius(dpToPx(PANEL_CORNER_DP));
        panel.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dpToPx(PANEL_WIDTH_DP),
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dpToPx(PANEL_SPACING_DP);
        panel.setLayoutParams(lp);

        panel.setVisibility(View.GONE);
        return panel;
    }

    private TextView buildToggleButton()
    {
        TextView toggle = new TextView(_activity);
        toggle.setText(TEXT_TOGGLE_COLLAPSED);
        toggle.setTextColor(Color.WHITE);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        toggle.setTypeface(Typeface.DEFAULT_BOLD);
        toggle.setGravity(Gravity.CENTER);

        int size = dpToPx(TOGGLE_SIZE_DP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        toggle.setLayoutParams(lp);

        float cornerPx = dpToPx(TOGGLE_CORNER_DP);
        float borderPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, BORDER_WIDTH_DP,
                _activity.getResources().getDisplayMetrics());
        toggle.setBackground(
                AdVisualsHelper.makeRoundedBackground(BUTTON_BG_COLOR, BORDER_COLOR, (int) cornerPx, borderPx));

        toggle.setOnClickListener(v -> onToggleTapped());
        return toggle;
    }

    private void populatePanel()
    {
        _panel.removeAllViews();
        if (_feedbackGiven)
        {
            TextView thanks = new TextView(_activity);
            thanks.setText(TEXT_THANK_YOU);
            thanks.setTextColor(Color.WHITE);
            thanks.setTextSize(TypedValue.COMPLEX_UNIT_SP, ITEM_TEXT_SIZE_SP);
            thanks.setGravity(Gravity.CENTER);
            thanks.setPadding(0, dpToPx(ITEM_PADDING_V_DP), 0, dpToPx(ITEM_PADDING_V_DP));
            _panel.addView(thanks);
        }
        else
        {
            _panel.addView(makeFeedbackRow(TEXT_INTERESTED,     true));
            _panel.addView(makeDivider());
            _panel.addView(makeFeedbackRow(TEXT_NOT_INTERESTED, false));
        }
    }

    private TextView makeFeedbackRow(String label, boolean interested)
    {
        TextView btn = new TextView(_activity);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, ITEM_TEXT_SIZE_SP);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, dpToPx(ITEM_PADDING_V_DP), 0, dpToPx(ITEM_PADDING_V_DP));

        TypedValue outValue = new TypedValue();
        _activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        btn.setBackgroundResource(outValue.resourceId);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> onFeedbackTapped(interested));
        return btn;
    }

    private View makeDivider()
    {
        View divider = new View(_activity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        lp.topMargin    = dpToPx(2);
        lp.bottomMargin = dpToPx(2);
        divider.setLayoutParams(lp);
        divider.setBackgroundColor(DIVIDER_COLOR);
        return divider;
    }

    // --- Interaction ---

    private void onToggleTapped()
    {
        if (_isOpen)
        {
            _isOpen = false;
            _toggleButton.setText(TEXT_TOGGLE_COLLAPSED);
            _panel.setVisibility(View.GONE);
            _panel.setAlpha(0f);
        }
        else
        {
            _isOpen = true;
            _toggleButton.setText(TEXT_TOGGLE_EXPANDED);
            populatePanel();

            _panel.setAlpha(0f);
            _panel.setVisibility(View.VISIBLE);
            _panel.animate()
                    .alpha(1f)
                    .setDuration(ANIM_DURATION_MS)
                    .start();
        }
    }

    private void onFeedbackTapped(boolean interested)
    {
        if (_feedbackGiven) return; // guard against double-tap firing the callback twice
        _feedbackGiven = true;
        populatePanel(); // swap buttons → thank-you message

        if (!interested && _listener != null)
            _listener.onNotInterested();
    }

    private int dpToPx(float dp)
    {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                _activity.getResources().getDisplayMetrics());
    }
}
