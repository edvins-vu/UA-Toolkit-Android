
Ad Type Button Visibility Matrix
1. Non-Rewarded Interstitial
isRewarded=false, isPlayable=false, isFlowB=false

┌────────┬──────────────────────────────────┬────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Button │            When shown            │                                                Trigger                                                 │
├────────┼──────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Skip   │ After skipButtonDelaySec elapses │ onCountdownTick when elapsed >= skipButtonDelaySec; or onCountdownComplete if equal delays (Bug 1 fix) │
├────────┼──────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Close  │ After user taps skip             │ onSkipClicked sets skipTapped=true → showCloseButton()                                                 │
└────────┴──────────────────────────────────┴────────────────────────────────────────────────────────────────────────────────────────────────────────┘

Sequence: nothing → skip → close → finish

  ---
2. Rewarded Video

isRewarded=true, isPlayable=false, isFlowB=false

┌────────┬────────────────────────┬─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Button │       When shown       │                                                         Trigger                                                         │
├────────┼────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Skip   │ After full video watch │ onVideoCompleted → showSkipButton(). Timer ticks and onCountdownComplete are both !isRewarded-guarded — never show skip │
├────────┼────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Close  │ After user taps skip   │ onSkipClicked sets skipTapped=true → showCloseButton()                                                                  │
└────────┴────────────────────────┴─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

Sequence: countdown text → "Reward earned!" + skip → close → finish (success)

  ---
3. Rewarded Playable

isPlayable=true, isFlowB=false

┌────────┬────────────────────────────────┬──────────────────────────────────────────────────────────────────────────────────────────────────┐
│ Button │           When shown           │                                             Trigger                                              │
├────────┼────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Skip   │ After closeButtonDelay elapses │ onCountdownComplete → isPlayable path → showSkipButton(). onCountdownTick guarded by !isPlayable │
├────────┼────────────────────────────────┼──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ Close  │ After user taps skip           │ onSkipClicked sets skipTapped=true → showCloseButton()                                           │
└────────┴────────────────────────────────┴──────────────────────────────────────────────────────────────────────────────────────────────────┘

Sequence: HTML loads → mute appears → skip after delay → close → finish

  ---
4. Flow B Video

isFlowB=true, isPlayable=false, isRewarded=true

┌────────────┬─────────────────────────────────────────┬─────────────────────────────────────────────────────────────────────────────────────────────────┐
│   Button   │               When shown                │                                             Trigger                                             │
├────────────┼─────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┤
│ OPEN STORE │ From start                              │ Created visible in createButtonContainer()                                                      │
├────────────┼─────────────────────────────────────────┼─────────────────────────────────────────────────────────────────────────────────────────────────┤
│ ✕ (Close)  │ After isFullyWatched && hasVisitedStore │ evaluateFlowBState() called from onVideoCompleted or onCollapsed → 1500ms delay → animated fade │
└────────────┴─────────────────────────────────────────┴─────────────────────────────────────────────────────────────────────────────────────────────────┘

Skip never shown (showSkipButton() guarded by if (isFlowB) return).
Sequence: OPEN STORE → visit store + watch video (any order) → 1500ms fade to ✕ → finish (always success)

  ---
5. Flow B Playable

isFlowB=true, isPlayable=true

┌────────────┬────────────────────────────────────────────┬───────────────────────────────────────────────────────────────────────────────────────────┐
│   Button   │                 When shown                 │                                          Trigger                                          │
├────────────┼────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────┤
│ OPEN STORE │ After HTML loads                           │ Starts GONE; showPlayableControls() makes it visible on onPageFinished                    │
├────────────┼────────────────────────────────────────────┼───────────────────────────────────────────────────────────────────────────────────────────┤
│ ✕ (Close)  │ After closeButtonEarned && hasVisitedStore │ evaluateFlowBState() called from onCountdownComplete or onCollapsed → 1500ms delay → fade │
└────────────┴────────────────────────────────────────────┴───────────────────────────────────────────────────────────────────────────────────────────┘


// Notes
Original Background Color: #80000000 (50% Transparent Black)

Original Button Color: #4CAF50 (Solid Green)

//  stage 1 popup being a circle instead  - 
"get_button": {
"text": "GET",
"button_color": "#00C853",
"width_dp": 80,
"height_dp": 80,
"text_size_sp": 20,
"corner_radius_dp": 100,
"text_color": "#FFFFFF"
},
"popup_card": {
"background_color": "#1A1A2E",
"corner_radius_dp": 100
}


//  get button being the blue color that was requested
{
"get_button": {
"text": "GET",
"button_color": "#473FFF",
"width_dp": 145,
"height_dp": 55,
"text_size_sp": 20,
"corner_radius_dp": 100,
"text_color": "#FFFFFF"
},
"popup_card": {
"background_color": "#80000000",
"corner_radius_dp": 100
}
}
}

// dark green button
"get_button": {
"text": "GET",
"button_color": "#556B2F",
"width_dp": 145,
"height_dp": 55,
"text_size_sp": 20,
"corner_radius_dp": 100,
"text_color": "#FFFFFF"
},
"popup_card": {
"background_color": "#80000000",
"corner_radius_dp": 100
}