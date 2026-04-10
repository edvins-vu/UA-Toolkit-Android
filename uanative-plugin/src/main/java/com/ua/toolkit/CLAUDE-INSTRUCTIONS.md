Resolve Playable Ad Audio ducking and UI Edge-Case Behaviors

Investigate and resolve reported issues regarding audio persistence and UI control failures in the new HTML5 Playable Ad workflow. The primary focus is ensuring that ad audio mutes/unmutes in sync with device hardware states and that the ad handles interruptions (phone calls, backgrounding) without breaking the WebView state.
Key Bug Areas & Scenarios
1. Hardware Audio Sync (Mute Switch/Volume):
Scenario: User toggles the physical Mute switch (iOS) or System Mute (Android) while the ad is playing.
Bug: Audio continues to play despite the device being set to silent.
Fix: Ensure the WebView's audio context is tied to the STREAM_MUSIC (Android) or AVAudioSession (iOS) categories and responds to volume change broadcasts.
2. App Switching & Interruption Lifecycle:
Scenario: The user receives a phone call or swipes to the home screen while a Playable Ad is active.
Bug: The ad's audio keeps playing in the background, or the WebView "freezes" and doesn't resume when the user returns.
Fix: Explicitly call webView.onPause()/onResume() (Android) and evaluateJavaScript("pauseGame()") (MRAID) to freeze the JS engine during background states.
3. "Ghost" Audio after Dismissal:
Scenario: The user closes the ad and returns to the game.
Bug: Interactive sounds from the playable continue to loop in the background of the main game.
Fix: Ensure the WebView is completely destroyed and the URL is cleared (loadUrl("about:blank")) when the PlayableAdActivity is destroyed.
4. Native UI vs. WebView Layering (Z-Order Clips):
Scenario: User interacts with the "GET" button or "X" button.
Bug: Tapping these native buttons occasionally registers as a "tap" inside the game underneath, or the buttons are non-responsive due to WebView focus.
Technical Validation Requirements
iOS: Verify WKWebView configuration for allowsInlineMediaPlayback and mediaTypesRequiringUserActionForPlayback.
Android: Verify WebSettings.setMediaPlaybackRequiresUserGesture(false) and AudioManager focus request logic.
Definition of Done (DoD)
[ ] Silent Mode: Ad audio is 100% silent when the device is muted via hardware or system tray.
[ ] Zero Background Leak: Confirmed via logs that the WebView process is killed and audio stops immediately upon ad close.
[ ] Recovery: Ad successfully resumes from the exact frame it was on after an app-switch/interruption.
[ ] Control Integrity: Verified that native SDK UI (Close/Mute/GET) always takes priority over the WebView's interactive layer.