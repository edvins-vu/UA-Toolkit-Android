// expected skip button logic 
1. Interstitial video



- Skip appears at skipButtonDelay elapsed

- Tap before closeButtonDelay: do nothing + single GET button pulse

- Tap after closeButtonDelay: skip → ✕



  ---

2. Non-rewarded playable



- Skip appears at closeButtonDelay elapsed, immediately functional

- Tap → ✕ (no "do nothing" path exists here)



  ---

3. Rewarded video — Flow A



- Skip appears when countdownRemaining == 0 AND isFullyWatched,
  immediately functional

- Tap → ✕ (no "do nothing" path exists here)



  ---

4. Rewarded video — Flow B



- Skip appears when flowBShowingClose, immediately functional

- Tap → dismissWithSuccess:YES (no "do nothing" path exists here)



  ---

5. Rewarded playable — Flow A



- Skip appears at closeButtonDelay elapsed, immediately functional

- Tap → ✕ (no "do nothing" path exists here)



  ---

6. Rewarded playable — Flow B


- Skip appears when flowBShowingClose, immediately functional

- Tap → dismissWithSuccess:YES (no "do nothing" path exists here)


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