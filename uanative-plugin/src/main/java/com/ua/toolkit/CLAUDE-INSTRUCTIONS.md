This task covers the architectural shift from a static Video Player to a dynamic WebView-based Engine. It ensures that your existing native UI components (the "Frame") are preserved while the "Content" becomes a fully interactive HTML5 environment.

Task: [Core-Android] Implement HTML5 Playable Ad Category & WebView Bridge
Task Name: SDK-ANDROID: Implement Playable Ad Engine with High-Performance WebView and MRAID-Lite Bridge

Description
Expand the Android SDK to support a new media_type: "playable" category. This involves creating a dedicated PlayableAdActivity that utilizes a hardware-accelerated WebView to host HTML5 games while maintaining the existing Flow B (Action-Gate) logic and native UI overlays.

Technical Requirements
1. High-Performance WebView Configuration:

Engine: Implement an android.webkit.WebView with hardwareAccelerated="true".

Settings: Enable setJavaScriptEnabled(true), setDomStorageEnabled(true), and setDatabaseEnabled(true) to support modern HTML5 game engines (Phaser, Three.js).

Media: Set setMediaPlaybackRequiresUserGesture(false) to allow the ad to play sound/video without an initial tap.

2. Layered UI & Touch-Through Management:

Z-Order: Use a FrameLayout to ensure the WebView stays at the bottom (index 0) while the UAAdPopup native components (Close button, "GET" button, Install Invitation) sit in a transparent layer on top (index 1).

Touch Priority: Ensure the native popup layer does not consume touch events unless a native button is specifically pressed, allowing the user to play the game underneath.

3. Playable-Specific "Engagement" Logic:

Disable Tap-to-Redirect: Unlike Video ads, tapping the background must not trigger the store; it must pass the touch to the game.

Auto-Show Invitation: Implement an automated trigger for the Install Invitation (GET Popup) that appears once show_install_popup_after_time_in_sec is reached, rather than waiting for a user tap.

Inactivity Nudge: (Optional) If no touch events are detected via dispatchTouchEvent for 5 seconds, trigger the Install Invitation as a nudge.

4. JS-to-Native Bridge (MRAID-Lite):

JavaScript Interface: Create a @JavascriptInterface to listen for calls from the HTML5 code (e.g., window.AdBridge.openStore()).

Redirect Logic: Map these JS calls to the existing handleStoreRedirect() method to satisfy the Flow B "Visited Store" requirement.

The "Flow B" Logic for Playables
Timer: Starts when the WebView finishes loading.

Gate: The "Close (X)" button remains hidden until BOTH the show_close_button_after_time_in_sec has elapsed AND the user has clicked "OPEN STORE" (either via the native UI or a link inside the playable game).

Definition of Done (DoD)
[ ] Rendering: Verified that a remote HTML5 URL loads and plays at 60 FPS without layout clipping.

[ ] Interaction: Confirmed that native buttons (Top-Right/Bottom-Center) are clickable while the game is still playable underneath.

[ ] Lifecycle: Confirmed that the WebView correctly pauses (onPause) and resumes (onResume) when the user switches apps or opens the Store.

[ ] Gating: Verified that the "X" button correctly transforms from "OPEN STORE" only after the dual-requirement (Time + Action) is satisfied.

[ ] Safety: Confirmed that the "Back" button is intercepted to prevent accidental ad exits during gameplay.