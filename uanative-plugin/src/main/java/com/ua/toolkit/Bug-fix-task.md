Investigate and resolve the fatal crash occurring when transitioning from the Unity Game Activity to the PlayableAdActivity using cached HTML assets. The investigation will focus on identifying the root cause—likely related to WebView initialization, local file permission violations, or Main Thread blocking—and refactoring the startup sequence for stability.

1. Main Thread Blocking & UI Deadlocks:
Problem: Loading a large cached HTML/JS file on the Main Thread can cause an Application Not Responding (ANR) or a watchdog kill.
Fix: Ensure the WebView.loadUrl() is called within a post() or runOnUiThread block, but verify that the file-system check (reading the cache) doesn't stall the UI.
2. Hardware Acceleration & Memory Pressure:
Problem: Unity and WebView both fight for GPU resources. Launching a hardware-accelerated WebView while the Unity Engine is still holding high-resolution textures can trigger an OutOMemory (OOM) crash or a GPU driver reset.
Refactor: Implement onPause() logic in Unity's bridge to release or "quiet" the game engine before the Intent starts.
3. WebView Local File Security (The "SecurityException"):
Problem: Modern Android (API 30+) strictly forbids file:// access unless specific WebSettings are set before the load starts.
Refactor: Ensure setAllowFileAccess(true) and setAllowUniversalAccessFromFileURLs(true) are called in the onCreate() of the Activity.
4. Context/Activity Null Pointers:
Problem: If the UANativeBridge attempts to start an Intent using a stale or null Activity context, the JVM will throw a fatal exception.
Refactoring Requirements
Isolated Process (Optional): Evaluate if running the PlayableAdActivity in a separate process via android:process=":playable_ad" in the Manifest helps isolate the crash from the main game.
The "Safe-Start" Wrapper: Wrap the Intent launch in a try-catch block and implement a fallback: if the Playable Activity fails to start, the SDK should immediately fire the onDismissed or onFailed callback to return the user to the game rather than crashing.
Memory Guard: Add a check for available RAM before initializing the WebView. If the device is critically low on memory, fallback to a static End Card or Video instead of the Playable.
Debug Checklist for the Developer
[ ] Logcat Filter: Search for FATAL EXCEPTION or backtrace specifically around the time of the startActivity call.
[ ] Hardware Toggle: Try disabling hardware acceleration (android:hardwareAccelerated="false") temporarily to see if the crash is GPU-related.
[ ] Path Validation: Verify that the path passed from C# (file:///...) is actually reachable and that the index.html exists at that exact location.
Definition of Done (DoD)
[ ] Zero-Crash Transition: Verified 20 consecutive transitions from Game → Playable Ad → Game without a process exit.
[ ] Error Handling: If the HTML file is missing or the WebView fails to init, the app returns to the game loop gracefully.
[ ] Memory Audit: Logcat shows no critical memory warnings during the WebView startup.