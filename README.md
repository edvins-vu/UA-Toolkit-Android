# UA.Toolkit.Android

Native Android plugin for Unity providing direct Play Store navigation and interstitial ad display capabilities.

---

## Overview

This Android library provides native functionality for the UA Toolkit Unity SDK:

1. **Native Store Launcher** (v1.0) - Resolves tracking URLs and opens Play Store directly
2. **Interstitial Ads** (Planned) - Native fullscreen video ad display

---

## Project Structure

```
UA.Toolkit.Android/
├── uanative-plugin/
│   └── src/main/java/com/ua/toolkit/
│       ├── UAStoreLauncher.java        # Entry point for store navigation
│       ├── HeadlessWebViewResolver.java # URL redirect resolver
│       ├── StoreOpener.java            # Play Store intent handler
│       ├── AdActivity.java             # [Planned] Interstitial activity
│       ├── AdVideoPlayer.java          # [Planned] Video playback
│       ├── AdUIManager.java            # [Planned] UI components
│       ├── AdTimerManager.java         # [Planned] Close button timer
│       ├── AdAudioManager.java         # [Planned] Audio handling
│       ├── AdCallback.java             # [Planned] Unity callback interface
│       └── AdConfig.java               # [Planned] Configuration model
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
- Proper Activity context handling (no unnecessary NEW_TASK flag)

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

## Interstitial Ads (Planned - v2.0)

Native fullscreen video ad display with complete lifecycle management.

### Components

#### AdActivity.java

Fullscreen Activity for displaying interstitial ads.

**Features:**
- Fullscreen immersive mode
- Video playback with audio
- Configurable close button delay
- Skip functionality
- Proper lifecycle handling

#### AdVideoPlayer.java

Video playback management using Android MediaPlayer.

**Responsibilities:**
- Stream video from URL
- Handle buffering states
- Loop playback
- Error recovery

#### AdUIManager.java

UI component management for the ad display.

**Components:**
- Video surface view
- Close button (with configurable delay)
- Skip button
- Loading indicator
- CTA button

#### AdTimerManager.java

Manages timed UI elements.

**Features:**
- Close button appearance delay
- Countdown display
- Minimum view time enforcement

#### AdAudioManager.java

Audio focus and volume management.

**Features:**
- Request audio focus during playback
- Restore audio state on completion
- Handle interruptions (calls, etc.)

#### AdCallback.java

Interface for communicating events back to Unity.

```java
public interface AdCallback {
    void onAdDisplayed();
    void onAdClicked();
    void onAdClosed();
    void onAdError(String error);
}
```

#### AdConfig.java

Configuration model for ad display behavior.

```java
public class AdConfig {
    int closeButtonDelaySeconds;
    boolean allowSkip;
    int skipAfterSeconds;
    String ctaText;
    String ctaUrl;
}
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

View logs:
```bash
adb logcat -s UAStoreLauncher:D HeadlessWebViewResolver:D StoreOpener:D
```

---

## Changelog

### v1.0.0
- Initial release
- Native Store Launcher implementation
- HeadlessWebViewResolver for tracking URL resolution
- StoreOpener with attribution preservation
- Browser fallback support

### v2.0.0 (Planned)
- Interstitial ad display via AdActivity
- Native video playback
- Configurable UI and timing
- Full Unity callback support

---

## License

Proprietary - Estoty
