# Flow B: Action-Gated Rewarded Ad — Implementation Spec (Android)

## Overview

Flow B is an optional rewarded ad variant that gates the close button behind two explicit user
actions: **watching the full video** and **visiting the Play Store**. Instead of the standard
skip → close countdown flow, a single dynamic corner button drives the entire exit sequence.

This feature is Android-only for this implementation pass. The existing non-rewarded interstitial
flow and the default rewarded flow (Flow A) are completely unaffected — all changes are guarded
behind the `isFlowB` flag.

---

## Corner Button State Machine

The top-right corner button has two states. Only one is active at any time.

| State | Label | Enabled | Action on tap |
|---|---|---|---|
| `OPEN_STORE` | "OPEN STORE" | Yes | Opens Play Store via `AdPopup.openStore()` |
| `CLOSE` | "✕" | Yes | `finishWithResult(true)` — grants reward |

### Transition rules

```
Initial state (ad opens)                         → OPEN_STORE
Store visited, video still playing               → OPEN_STORE  (no change — button remains actionable)
Video finishes, store not yet visited            → OPEN_STORE  (no change — button remains actionable)
isVideoFinished && hasVisitedStore               → wait 1500 ms → crossfade → CLOSE
```

The button remains `OPEN_STORE` (enabled, fully visible) until **both** flags are true — regardless of
which condition is satisfied first. It never becomes disabled or greyed out. The only path to `CLOSE`
requires **both** flags.

---

## New Config Field

### `AdConfig.java`
Add one field alongside `isRewarded`:

```java
public final boolean isFlowB;
```

- Parsed in the constructor from a new `boolean isFlowB` parameter.
- No validation needed — boolean flag.

### `AdActivity.parseIntentConfig()`
Add to the intent read block:

```java
getIntent().getBooleanExtra("IS_FLOW_B", false),
```

Position: alongside `IS_REWARDED` in the Core section.

---

## Android Implementation

### 1. `AdConfig.java`
- Add `public final boolean isFlowB` field.
- Add `boolean isFlowB` parameter to the constructor (after `isRewarded`).
- Assign: `this.isFlowB = isFlowB;`

---

### 2. `AdPopupLayout.java`
Add two new constants (not remote-overridable):

```java
// --- Flow B transition ---
final int flowBTransitionDelayMs = 1500; // delay before CLOSE button appears after both flags are true
final int flowBFadeDurationMs    = 300;  // crossfade animation duration for OPEN_STORE → CLOSE transition
```

---

### 3. `AdPopup.java`
Add one new public method. This is the entry point when the corner OPEN_STORE button is tapped:

```java
/**
 * Programmatically opens the Play Store from any popup state.
 * Tracks the click (once) and fires the appropriate state transition.
 * Used by AdActivity in Flow B when the corner "OPEN STORE" button is tapped.
 */
public void openStore() {
    if (_isCancelled) return;
    if (!_isAdClicked) {
        _isAdClicked = true;
        _listener.onAdClicked();
    }
    switch (_state) {
        case HIDDEN:
            // Peek first, then open store after slide-in completes
            peek();
            _handler.postDelayed(this::launchPlayOverlay, _layout.slideInDurationMs);
            break;
        case PEEK:
        case COLLAPSED:
            launchPlayOverlay();
            break;
        case PLAY_OVERLAY:
            break; // already open — no-op
    }
}
```

`launchPlayOverlay()` must be changed from `private` to package-private (remove `private` modifier)
so `openStore()` can reference it via method reference in the `postDelayed` call above.

---

### 4. `AdUIManager.java`

#### 4a. New enum (inner or top-level in the display package)
```java
public enum CornerButtonState { OPEN_STORE, CLOSE }
```

#### 4b. New fields
```java
private boolean isFlowB = false;
private CornerButtonState cornerButtonState = CornerButtonState.OPEN_STORE;
```

#### 4c. New setter (called from `AdActivity.initializeManagers()`)
```java
public void setFlowB(boolean flowB) { this.isFlowB = flowB; }
```

#### 4d. `createButtonContainer()` changes
In Flow B, the corner button must be **visible from ad start** (not hidden behind a countdown).
After creating `closeButton`, add:

```java
if (isFlowB) {
    // Corner button starts as OPEN_STORE — visible immediately, skip button suppressed
    closeButton.setText("OPEN STORE");
    closeButton.setVisibility(View.VISIBLE);
    skipButton.setVisibility(View.GONE);
}
```

The existing `closeButton` view is reused as the corner button in Flow B. No new view needed.

#### 4e. `showSkipButton()` — guard for Flow B
```java
public void showSkipButton() {
    if (isFlowB) return;   // ← add this guard
    // ... existing logic
}
```

#### 4f. `showCloseButton()` — guard for Flow B
```java
public void showCloseButton() {
    if (isFlowB) return;   // ← add this guard
    // ... existing logic
}
```

#### 4g. New method: `setCornerButtonState(CornerButtonState state)`
```java
public void setCornerButtonState(CornerButtonState state) {
    if (!isFlowB || closeButton == null) return;
    cornerButtonState = state;
    switch (state) {
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
```

#### 4h. New method: `transitionCornerButtonToClose()`
Runs the crossfade animation, then calls `setCornerButtonState(CLOSE)`.

```java
public void transitionCornerButtonToClose(int fadeDurationMs) {
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
```

#### 4i. Corner button click routing
The `closeButton.setOnClickListener` currently always calls `listener.onCloseClicked()`. This
remains correct — `AdActivity.onCloseClicked()` will handle Flow B routing (see section 5f).

---

### 5. `AdActivity.java`

#### 5a. New fields
```java
private boolean hasVisitedStore     = false;
private boolean transitionPending   = false; // guards against double-posting the 1500ms delay
private final Handler flowBHandler  = new Handler(Looper.getMainLooper());
```

#### 5b. `initializeManagers()` — pass `isFlowB` to AdUIManager
After creating `uiManager`, before `setupUI()`:
```java
uiManager.setFlowB(config.isFlowB);
```

#### 5c. `onSaveInstanceState()` — persist `hasVisitedStore`
```java
outState.putBoolean("hasVisitedStore", hasVisitedStore);
```

#### 5d. `onCreate()` restore block — restore `hasVisitedStore`
```java
hasVisitedStore = savedInstanceState.getBoolean("hasVisitedStore", false);
```

#### 5e. Popup `onCollapsed()` listener — set `hasVisitedStore`
```java
@Override
public void onCollapsed() {
    hasVisitedStore = true;         // ← add before existing logic
    if (config.isFlowB) evaluateFlowBState();
    // ... existing onCollapsed logic unchanged
}
```

#### 5f. `onCloseClicked()` — route Flow B tap
```java
@Override
public void onCloseClicked() {
    if (config.isFlowB) {
        handleFlowBCornerButtonTap();
        return;
    }
    // existing non-Flow-B logic unchanged
    boolean success = config.isRewarded ? isFullyWatched : true;
    finishWithResult(success);
}
```

New method:
```java
private void handleFlowBCornerButtonTap() {
    AdUIManager.CornerButtonState state = uiManager.getCornerButtonState();
    if (state == AdUIManager.CornerButtonState.OPEN_STORE) {
        if (popup != null) popup.openStore();
    } else if (state == AdUIManager.CornerButtonState.CLOSE) {
        finishWithResult(true); // reward always granted in Flow B close
    }
    // FINISH_VIDEO state: button is disabled — tap should not reach here
}
```

Add a getter to `AdUIManager`:
```java
public CornerButtonState getCornerButtonState() { return cornerButtonState; }
```

#### 5g. `onVideoCompleted()` — evaluate Flow B after video finishes
```java
@Override
public void onVideoCompleted() {
    // ... existing isFullyWatched / reward logic unchanged
    if (config.isFlowB) evaluateFlowBState();
}
```

#### 5h. New method: `evaluateFlowBState()`
```java
private void evaluateFlowBState() {
    if (!config.isFlowB) return;

    if (isFullyWatched && hasVisitedStore) {
        if (!transitionPending) {
            transitionPending = true;
            flowBHandler.postDelayed(() -> {
                transitionPending = false;
                uiManager.transitionCornerButtonToClose(_layout.flowBFadeDurationMs);
            }, _layout.flowBTransitionDelayMs);
        }
    }
    // Either flag not yet true: stays OPEN_STORE — button remains enabled, no change needed
}
```

Where `_layout` is a reference to `AdPopupLayout` — or the delay/fade constants can be read
from a new `AdActivityLayout` or simply hardcoded here. Simplest option: inline the values from
`AdPopupLayout.flowBTransitionDelayMs` / `flowBFadeDurationMs` via the popup's layout, or
duplicate them as local constants in `AdActivity`.

#### 5i. `onCountdownComplete()` — suppress close button in Flow B
```java
@Override
public void onCountdownComplete() {
    closeButtonEarned = true;
    if (config.isFlowB) return;   // ← Flow B ignores countdown-based close
    if (popup == null || !popup.isExpanded()) uiManager.showCloseButton();
}
```

#### 5j. `handleBackNavigation()` — lock back button in Flow B
```java
private void handleBackNavigation() {
    if (config.isFlowB) return; // ← back is always blocked in Flow B
    // ... existing logic unchanged
}
```

#### 5k. `onDestroy()` — clean up Flow B handler
```java
flowBHandler.removeCallbacksAndMessages(null);
```

---

## Unity C# Layer

### 6. `ShowAdRequest.cs`
Add one field to the Core section:

```csharp
public bool IsFlowB { get; }
```

Add to the constructor parameter list (after `IsRewarded`) and assign in the body.

### 7. `UANativeBridge.cs`
Add one intent extra in `ShowAd()`, in the Core section:

```csharp
intent.Call<AndroidJavaObject>("putExtra", "IS_FLOW_B", request.IsFlowB);
```

---

## Definition of Done

- [ ] **Condition A** — "✕" does not appear if the user watched the full video but never tapped "OPEN STORE". Corner button stays at "OPEN STORE".
- [ ] **Condition B** — "✕" does not appear if the user tapped "OPEN STORE" but the video has not finished yet. Corner button remains "OPEN STORE" (enabled) until video completes. The button is never disabled or greyed out at any point.
- [ ] **Condition C** — Once both flags are true, the corner button transitions to "✕" after exactly 1500 ms with a crossfade animation.
- [ ] **Condition D** — `onAdFinished(true)` (reward granted) only fires when the user taps "✕" after earning it. Tapping "OPEN STORE" never grants the reward.
- [ ] **Condition E** — Video timer pauses when the Play Store opens (`onExpanded`) and resumes correctly on return (`onCollapsed`). Timer pause/resume is unchanged from the existing flow.
- [ ] **Condition F** — The system Back button is fully blocked throughout a Flow B ad. The user cannot bypass the gate via system navigation.
- [ ] **Condition G** — Process death (OS kills activity while store is open) correctly restores `hasVisitedStore = true` and `isFullyWatched` from `savedInstanceState`, so the button resumes at the correct state.
- [ ] **Condition H** — All existing non-Flow-B rewarded and interstitial ads are unaffected. `config.isFlowB == false` takes every existing code path without change.
- [ ] **Condition I** — `AdPopup` GET button and corner "OPEN STORE" button are independent triggers for the same store intent. Either one correctly sets `hasVisitedStore` via `onCollapsed`.

---

## Files Modified Summary

| File | Change |
|---|---|
| `AdConfig.java` | + `isFlowB` field |
| `AdPopupLayout.java` | + `flowBTransitionDelayMs`, `flowBFadeDurationMs` |
| `AdPopup.java` | + `openStore()` public method; `launchPlayOverlay` → package-private |
| `AdUIManager.java` | + `CornerButtonState` enum (`OPEN_STORE`, `CLOSE`); + `isFlowB` field; guarded `showCloseButton`/`showSkipButton`; + `setCornerButtonState`, `transitionCornerButtonToClose`, `getCornerButtonState` |
| `AdActivity.java` | + `hasVisitedStore` flag; + `evaluateFlowBState`, `handleFlowBCornerButtonTap`; modified `onCloseClicked`, `onCollapsed`, `onVideoCompleted`, `onCountdownComplete`, `handleBackNavigation`; state save/restore |
| `ShowAdRequest.cs` | + `IsFlowB` field |
| `UANativeBridge.cs` | + `IS_FLOW_B` intent extra |

**No new files required.**

---

# Playable Ad Crash Fix — Android (Implemented) / iOS Delta

## Summary of Android fixes implemented (2026-04-08)

The following bugs were identified and fixed on the Android side. The iOS native layer
(`UAAdPopup`, `UANativeBridgeIOS`, the Objective-C ad view controller) will need equivalent
changes where the same patterns apply.

---

## Fix 1 — Correct URI format for local HTML loading (CRITICAL)

**Android change (`AdActivity.java` — `startAd()`):**
- `webView.loadUrl(config.videoPath)` was called with a raw filesystem path (e.g. `/data/user/0/com.pkg/files/abc.html`)
- WebView requires a proper URI scheme: `file:///data/user/0/...`
- Fixed by using `Uri.fromFile(new File(config.videoPath)).toString()` which produces the correct three-slash form

**iOS delta:**
- `UANativeBridgeIOS` passes `videoPath` (local filesystem path) to native via P/Invoke `UA_ShowAd()`
- The native Objective-C layer must load the HTML using `[NSURL fileURLWithPath:htmlPath]`, NOT a bare `NSString` path passed directly to `WKWebView loadRequest:`
- Verify the iOS ad view controller does: `NSURL *url = [NSURL fileURLWithPath:htmlPath]; [_webView loadFileURL:url allowingReadAccessToURL:url.URLByDeletingLastPathComponent];`
- `loadFileURL:allowingReadAccessToURL:` (iOS 9+) is the correct API — it grants read access to the containing directory so sub-resources (JS, CSS, images) can be loaded

---

## Fix 2 — WebView/WKWebView local file access settings (CRITICAL)

**Android change (`AdActivity.java` — `initializeManagers()`):**
- Added `ws.setAllowFileAccess(true)` and `ws.setAllowUniversalAccessFromFileURLs(true)` to WebSettings
- Without these, API 30+ blocks all `file://` access with a SecurityException

**iOS delta:**
- `WKWebView` on iOS handles local file access differently — `loadFileURL:allowingReadAccessToURL:` (Fix 1) grants the required access without additional configuration flags
- However, verify `WKPreferences` has JavaScript enabled: `preferences.javaScriptEnabled = YES` (or `WKWebpagePreferences.allowsContentJavaScript = YES` on iOS 14+)
- No equivalent of `setAllowUniversalAccessFromFileURLs` exists on iOS — `loadFileURL:allowingReadAccessToURL:` with the parent directory as the access URL covers sub-resource loading

---

## Fix 3 — Decouple WebView content load from manager initialization (IMPORTANT)

**Android change (`AdActivity.java`):**
- `webView.loadUrl()` was called inside `initializeManagers()`, before `popup.attach(config)` and inset callbacks were registered
- Moved `loadUrl` to `startAd()` so content loading begins only after all managers and popup are fully wired up
- Mirrors the existing video ad pattern

**iOS delta:**
- Verify the iOS ad view controller does NOT call `[_webView loadFileURL:...]` inside the equivalent of `viewDidLoad` / initialization before the popup and timer are set up
- The load should begin in a dedicated `startAd` equivalent, called after all UI setup is complete

---

## Fix 4 — Load watchdog covers playable WebView (IMPORTANT)

**Android change (`AdActivity.java`):**
- The existing 15-second `prepareWatchdog` (which fires `onAdFailed` + `finishWithResult(false)`) previously only fired for video ads
- Now also fires for playable: `prepareWatchdog.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS)` is called in `startAd()` for the playable path
- Watchdog is cancelled in `onContentReady()` (called from `WebViewClient.onPageFinished`) via `prepareWatchdog.removeCallbacks(prepareTimeoutRunnable)`

**iOS delta:**
- Add an equivalent load timeout for the playable WKWebView
- Use `dispatch_after` or an `NSTimer` (15 seconds) started when `loadFileURL:` is called
- Cancel the timer in `webView:didFinishNavigation:` (WKNavigationDelegate)
- On timeout: call the equivalent of `onAdFailed` and dismiss the view controller

---

## Fix 5 — WebView creation wrapped in try-catch for graceful fallback (ROBUSTNESS)

**Android change (`AdActivity.java` — `initializeManagers()`):**
- `new WebView(this)` and all WebSettings setup now wrapped in try-catch
- On exception: fires `callback.onAdFailed(msg)` and `finishWithResult(false)` instead of crashing
- `startAd()` guards with `if (resultSent) return` and `if (webView == null) return` to prevent continuation after failure

**iOS delta:**
- Wrap `WKWebView` allocation and configuration in `@try/@catch` or check for nil after init
- If WKWebView fails to initialize, call the failure callback and dismiss immediately rather than showing a blank view controller

---

## Fix 6b — Back navigation reward gate uses `engagementMet` pattern (BUG FIX)

**Android change (`AdActivity.java` — `handleBackNavigation()`):**
- `handleBackNavigation()` previously used raw `isFullyWatched` as the reward gate when the back button was pressed while the close button was visible
- For rewarded playable ads `isFullyWatched` is never set (only `closeButtonEarned` is), so rewarded playable users pressing back after earning the reward would be denied it
- Fixed: `boolean engagementMet = isPlayable ? closeButtonEarned : isFullyWatched;` applied consistently, matching `onCloseClicked()` and `popup.onDismissed()`

**iOS delta:**
- iOS has no back button; this code path does not exist — no change needed

---

## Fix 6 — Cached HTML path validated before Intent launch (ROBUSTNESS)

**C# change (`UASDK.cs` — `ShowPlayable()`):**
- Added `File.Exists(state.CachedVideoPath)` check after `ValidateShowGuards()` passes
- If the cached file is missing (evicted by OS storage pressure between load and show): resets `IsReady` state, fires `OnPlayableDisplayFailedEvent`, invokes `onComplete(false)`
- Guard lives in `UASDK` (business logic layer), not `UANativeBridge` (transport layer)

**iOS delta:**
- Same C# fix applies to both platforms — `UASDK.ShowPlayable()` is platform-agnostic
- No additional iOS native change needed for this fix; the C# guard fires before `UANativeBridgeIOS` is ever called

---

## iOS Delta — Files to modify

| File | Change needed |
|---|---|
| iOS native ad view controller (`.m`/`.mm`) | Use `loadFileURL:allowingReadAccessToURL:` instead of bare path load (Fix 1 + 2) |
| iOS native ad view controller | Move `loadFileURL:` call to `startAd` equivalent, after UI setup (Fix 3) |
| iOS native ad view controller | Add 15s `NSTimer` watchdog, cancel in `didFinishNavigation:`, fire failure on timeout (Fix 4) |
| iOS native ad view controller | Wrap `WKWebView` alloc/init in `@try/@catch`, call failure callback if nil (Fix 5) |
| `UASDK.cs` | Already fixed — `File.Exists` guard in `ShowPlayable()` covers iOS too (Fix 6 ✓) |
