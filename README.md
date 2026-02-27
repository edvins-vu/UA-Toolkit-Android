# UA.Toolkit.Android

Native Android plugin for Unity providing direct Play Store navigation and interstitial ad display with an in-app store popup flow.

---

## Overview

This Android library provides native functionality for the UA Toolkit Unity SDK:

1. **Native Store Launcher** (v1.0) - Resolves tracking URLs and opens Play Store directly
2. **Interstitial Ads** (v2.0) - Native fullscreen video ad with a 3-stage in-app store popup

---

## Project Structure

```
UA.Toolkit.Android/
├── uanative-plugin/
│   └── src/main/java/com/ua/toolkit/
│       ├── AdActivity.java             # Fullscreen interstitial activity
│       ├── AdCallback.java             # Unity callback interface
│       ├── AdConfig.java               # Ad configuration model
│       ├── UAStoreLauncher.java        # Entry point for store navigation
│       ├── display/
│       │   ├── AdAudioManager.java     # Audio focus and mute handling
│       │   ├── AdTimerManager.java     # Close button / reward timers
│       │   ├── AdUIManager.java        # Fullscreen UI components
│       │   └── AdVideoPlayer.java      # MediaPlayer wrapper
│       ├── popup/
│       │   └── AdPopup.java            # 3-stage in-app store popup
│       └── store/
│           ├── HeadlessWebViewResolver.java  # URL redirect resolver
│           ├── InstalledAppsChecker.java     # Installed package detection
│           └── StoreOpener.java              # Play Store intent handler
└── build.gradle
```

---

## Native Store Launcher (v1.0)

### Purpose

Provides seamless Play Store navigation by resolving tracking/attribution URLs (Adjust, AppsFlyer, etc.) and opening the store directly without browser intermediaries.

### Components

#### UAStoreLauncher.java

Entry point called from Unity via JNI.

```java
public static void openLink(Context context, String url, Callback callback)
```

| Parameter | Description |
|-----------|-------------|
| `context` | Android context (Unity activity) |
| `url` | Tracking URL to resolve (e.g., Adjust link) |
| `callback` | Callback interface for success/failure |

**Callback Interface:**
```java
public interface Callback {
    void onSuccess(String packageId);
    void onFailed(String reason);
}
```

#### HeadlessWebViewResolver.java

Resolves tracking URLs by following HTTP redirects using a hidden WebView.

**Features:**
- Creates invisible WebView to follow redirects
- Intercepts Play Store URLs in redirect chain
- Extracts `packageId` and `referrer` parameters
- Configurable timeout (default: 15 seconds)
- Automatic cleanup on completion

**Supported URL Patterns:**
- `play.google.com/store/apps/details?id=...`
- `market.android.com/...`
- `market://details?id=...`

#### StoreOpener.java

Opens the Play Store using native Android intents.

**Features:**
- Primary: `market://` scheme with Play Store package restriction
- Fallback: HTTPS URL with Play Store package restriction
- Preserves `referrer` parameter for attribution

**Intent Flow:**
```
market://details?id=com.example.app&referrer=adjust_tracker%3Dabc123
    ↓
Intent.ACTION_VIEW
    ↓
setPackage("com.android.vending")  // Force Play Store only
    ↓
context.startActivity(intent)
```

### Attribution Preservation

The launcher preserves install attribution by:

1. Extracting `referrer` from resolved Play Store URL
2. Passing it in the `market://` intent
3. Google Play Install Referrer API receives the referrer
4. Attribution SDK reads it on first app launch

### Fallback Behavior

```
URL Resolution
    ↓
[Success] → Open Play Store → callback.onSuccess(packageId)
    ↓
[Failure] → Open Browser → callback.onSuccess("browser-fallback")
    ↓
[Both Fail] → callback.onFailed(reason)
```

---

## Interstitial Ads (v2.0)

Native fullscreen video ad display with a 3-stage in-app store popup that keeps the user inside the app.

### Ad Lifecycle

```
AdActivity.onCreate()
    ↓
Video loads (MediaPlayer)
    ↓
onVideoPrepared → callback.onAdStarted() + timer starts + popup.schedulePeek()
    ↓
[Video plays, timer counts down]
    ↓
onCountdownComplete → close button appears
    ↓
User closes → callback.onAdFinished(success)
```

### 3-Stage Popup Flow

The `AdPopup` class manages the in-app store promotion overlay, displayed over the playing video without requiring the user to leave the app.

```
Stage 1 — PEEK
    Small card (≤30% screen width) slides in at bottom-right corner.
    Shows: app icon + GET button.
    User taps GET (or taps video) → Stage 2.

Stage 2 — EXPANDED
    Full-height bottom sheet slides up (80% screen height).
    Contains: drag handle + WebView loading the store click URL.
    Video and timers are paused.
    WebView follows Adjust/tracker redirect chain to Play Store page.
    User taps Install inside the WebView → Stage 3.
    User drags sheet down >40% → Stage 3.

Stage 3 — NATIVE_FALLBACK
    Sheet slides away, video resumes.
    Full-width card at the bottom shows: icon + app name + GET button.
    GET opens Play Store via StoreOpener (market:// intent).
    Card stays visible until the ad close button is tapped.
    User can tap GET multiple times (e.g., after returning from the store).
```

#### WebView redirect handling

- `shouldOverrideUrlLoading` intercepts `market://` and `intent://` URLs and routes them to Stage 3 instead of leaving the app.
- All other URLs (Adjust redirect chain, Play Store pages) are allowed to navigate normally (`return false`).
- A `_storePageLoaded` flag is set in `onPageFinished` when the Play Store page URL is confirmed. This prevents the automatic `intent://` redirect the Play Store page fires on load from prematurely triggering Stage 3.
- App name is captured from the Play Store page title via `WebChromeClient.onReceivedTitle()` for display in the Stage 3 card.
- Package ID (`_targetPackage`) is late-extracted from the resolved Play Store URL `?id=` parameter in `onPageFinished`, so tracker URLs without `?id=` (e.g. Adjust links) still work correctly.

#### Backgrounding while Stage 2 is open

When the app is backgrounded (phone call, home button, notification), the standard Activity lifecycle fires:

- `onPause()` → pauses video, timer, and calls `popup.pauseWebContent()` → `WebView.onPause()` (suspends JS execution and network activity).
- `onResume()` with Stage 2 open → calls `popup.resumeWebContent()` → `WebView.onResume()` (restores WebView). Video and timer remain paused since the popup is still expanded.
- `onResume()` with Stage 2 closed → resumes video and timer as normal.

### Components

#### AdActivity.java

Fullscreen Activity hosting the ad.

**Features:**
- Orientation locked to device rotation at launch
- Immersive fullscreen with display cutout support
- `prepareWatchdog` times out MediaPlayer prepare after 15s if neither `onPrepared` nor `onError` fires
- Back navigation blocked until close button is earned; popup back press handled first
- Static `dismissAd()` method for external cancellation (e.g., scene switch)

#### AdVideoPlayer.java

MediaPlayer wrapper for local video file playback.

**Responsibilities:**
- Load and prepare video from file path
- Loop playback
- Expose `getCurrentPosition()`, `getLastPausedPosition()`, `getDuration()`
- Surface lifecycle management

#### AdUIManager.java

Fullscreen UI components for the ad.

**Components:**
- `VideoView` (surface for MediaPlayer)
- Close button with configurable delay
- Reward earned label (rewarded ads)
- Countdown and reward timer displays
- Mute button
- Insets/fullscreen reapplication on resume

#### AdTimerManager.java

Manages the close-button countdown and reward timer.

**Features:**
- Configurable close button delay (non-rewarded: countdown; rewarded: counts video watch time)
- Pause/resume support
- Reward timer based on video playback position (uses `max(currentPos, lastPausedPos)` to avoid keyframe jump artifacts)

#### AdAudioManager.java

Audio focus and volume management.

**Features:**
- Requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` on start
- Pauses/resumes playback on audio focus loss/gain
- Mute toggle via `MediaPlayer.setVolume()`
- Released on ad finish

#### AdCallback.java

Interface for communicating events back to Unity.

```java
public interface AdCallback {
    void onAdStarted();             // Video prepared and playing
    void onAdFinished(boolean success); // Ad closed (success = user earned reward or watched interstitial)
    void onAdFailed(String error);  // Setup or playback error
    void onAdClicked();             // User tapped GET / opened store popup
}
```

#### AdConfig.java

Configuration passed via `Intent` extras.

| Field | Intent Extra | Default | Description |
|-------|-------------|---------|-------------|
| `videoPath` | `VIDEO_PATH` | — | Absolute path to local video file |
| `clickUrl` | `CLICK_URL` | — | Attribution/tracker URL for store popup |
| `isRewarded` | `IS_REWARDED` | `false` | Rewarded ad — success only if fully watched |
| `closeButtonDelay` | `CLOSE_BUTTON_DELAY` | `5` | Seconds before close button appears |
| `iconPath` | `ICON_PATH` | — | Absolute path to app icon for popup cards |
| `peekDelay` | `POPUP_PEEK_DELAY` | `5` | Seconds after playback starts before Stage 1 appears |

#### AdPopup.java

Manages the 3-stage in-app store overlay (see flow above).

**Public API:**

```java
void attach(AdConfig config)          // Build and attach views to root layout
void schedulePeek(int delaySeconds)   // Schedule Stage 1 appearance
boolean isExpanded()                  // True while Stage 2 WebView is visible
boolean handleBackPress()             // Consume back in Stage 2; defer in Stage 3
void handleVideoTap()                 // Video surface tap handler
void pauseWebContent()                // Call from Activity.onPause()
void resumeWebContent()               // Call from Activity.onResume()
void cancel()                         // Tear down all views and cancel pending work
```

---

## Building

### Requirements

- Android Studio Arctic Fox or newer
- Android SDK 21+ (minSdk)
- Android SDK 34 (targetSdk)
- Gradle 7.0+

### Build AAR

```bash
./gradlew :uanative-plugin:assembleRelease
```

Output: `uanative-plugin/build/outputs/aar/uanative-plugin-release.aar`

### Integration with Unity

1. Copy the `.aar` file to Unity project:
   ```
   Packages/ua-toolkit-sdk/Runtime/Plugins/Android/ua-toolkit.aar
   ```

2. Ensure the `.aar` is configured for Android platform only in Unity's Plugin Inspector.

---

## Unity Integration

### C# Bridge (UAStoreLauncher.cs)

```csharp
public static void OpenLink(
    string url,
    Action onClick = null,
    Action<string> onSuccess = null,
    Action<string> onFailed = null
)
```

### Thread Marshaling

Android callbacks execute on JNI threads. The Unity SDK includes `UnityMainThreadDispatcher` to marshal callbacks to Unity's main thread.

---

## Logging

All components use Android's `Log` class with consistent tags:

| Component | Tag |
|-----------|-----|
| UAStoreLauncher | `UAStoreLauncher` |
| HeadlessWebViewResolver | `HeadlessWebViewResolver` |
| StoreOpener | `StoreOpener` |
| AdActivity | `AdActivity` |
| AdPopup | `AdPopup` |
| AdVideoPlayer | `AdVideoPlayer` |
| AdUIManager | `AdUIManager` |
| AdTimerManager | `AdTimerManager` |
| AdAudioManager | `AdAudioManager` |

View logs:
```bash
adb logcat -s UAStoreLauncher:D HeadlessWebViewResolver:D StoreOpener:D AdActivity:D AdPopup:D
```

---

## Changelog

### v1.0.0
- Native Store Launcher implementation
- HeadlessWebViewResolver for tracking URL resolution
- StoreOpener with attribution preservation
- Browser fallback support

### v2.0.0
- Interstitial ad display via AdActivity
- Native video playback (MediaPlayer)
- Rewarded and non-rewarded ad modes
- Configurable close button delay, orientation lock, display cutout support
- Prepare watchdog timeout (15s)
- 3-stage in-app store popup (AdPopup): corner peek card → WebView sheet → native fallback card
- Adjust tracker URL support with late package ID resolution
- App name capture from Play Store page title
- Automatic `intent://` / `market://` interception to prevent leaving app
- WebView lifecycle hooks (`pauseWebContent` / `resumeWebContent`) for correct backgrounding behaviour
- Package restructure into `display/`, `popup/`, `store/` subpackages

---

## License

Proprietary - Estoty
