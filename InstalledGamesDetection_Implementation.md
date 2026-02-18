# Installed Games Detection - Implementation Plan (Android + iOS)

## Context
The UA Toolkit ad network needs to filter ads so users don't see ads for games they already have installed. Currently this is done server-side via analytics. The goal is to check installed games locally on the device and send that list in the ad request payload, so the server can filter more efficiently.

---

## Android Implementation

### 1. New Java class: `InstalledAppsChecker.java`
**Path:** `uanative-plugin/src/main/java/com/ua/toolkit/InstalledAppsChecker.java`

Static utility that takes comma-separated package names and returns the subset that are installed on the device via `PackageManager.getPackageInfo()`. Uses CSV strings for easy JNI marshalling.

### 2. AndroidManifest.xml update
**Path:** `uanative-plugin/src/main/AndroidManifest.xml`

Add your game package names to the existing `<queries>` block. Required since Android 11 (API 30) for package visibility. No practical limit on entries. Example:

```xml
<queries>
    <package android:name="com.android.vending" />
    <package android:name="com.android.chrome" />
    <!-- Your games -->
    <package android:name="com.yourstudio.game1" />
    <package android:name="com.yourstudio.game2" />
</queries>
```

---

## iOS Implementation

### 3. Native Objective-C helper: `InstalledAppsChecker.m`
**Path:** `UA.Toolkit.Intersititals.iOS/Plugins/iOS/InstalledAppsChecker.m`

C-linkage function callable from Unity C# via `[DllImport("__Internal")]`. Uses `UIApplication.canOpenURL()` to check each URL scheme. Returns comma-separated list of installed schemes.

### 4. PostProcessBuild script: `UAToolkitPostProcess.cs`
**Path:** `UA.Toolkit.Intersititals.iOS/Editor/UAToolkitPostProcess.cs`

Runs after iOS build to inject `LSApplicationQueriesSchemes` into `Info.plist`. This is where the master list of URL schemes is maintained. Updating the SDK version automatically updates the scheme list in all games that use the SDK.

**Important:** Max 50 URL schemes allowed by Apple.

### 5. URL scheme registration: `UAToolkitURLScheme.cs`
**Path:** `UA.Toolkit.Intersititals.iOS/Editor/UAToolkitURLScheme.cs`

PostProcessBuild script that registers the **current game's own URL scheme** in `CFBundleURLTypes`, so other SDK-integrated games can detect it as installed.

Scheme naming convention: `com.yourstudio.game1` -> `yourstudio-game1` (drops first segment, joins with hyphens).

**Requirement:** Each game in your catalog must have the SDK integrated for detection to work. The SDK handles scheme registration automatically.

---

## Cross-Platform C# Bridge

### 6. Unified C# class: `InstalledAppsChecker.cs`
**Path:** `UA.Toolkit.Intersititals.Android/InstalledAppsChecker.cs`

Single C# class with platform-specific implementations:
- **Android**: calls Java `InstalledAppsChecker` via `AndroidJavaClass`
- **iOS**: calls native `CheckInstalledSchemes` via `[DllImport]`
- **Editor**: returns empty list

---

## Usage from Game Code

```csharp
// 1. Get game catalog from your API (list of package IDs / URL schemes)
var catalog = await FetchGameCatalog();

// 2. Check which are installed locally
#if UNITY_ANDROID
    var installed = InstalledAppsChecker.GetInstalledGames(catalog.packageNames);
#elif UNITY_IOS
    var installed = InstalledAppsChecker.GetInstalledGames(catalog.urlSchemes);
#endif

// 3. Include in ad request payload
adRequestPayload.installed_games = installed;
```

---

## Files Summary

| # | File | Action | Platform |
|---|------|--------|----------|
| 1 | `uanative-plugin/.../InstalledAppsChecker.java` | Create | Android |
| 2 | `uanative-plugin/src/main/AndroidManifest.xml` | Modify | Android |
| 3 | `UA.Toolkit.Intersititals.iOS/Plugins/iOS/InstalledAppsChecker.m` | Create | iOS |
| 4 | `UA.Toolkit.Intersititals.iOS/Editor/UAToolkitPostProcess.cs` | Create | iOS |
| 5 | `UA.Toolkit.Intersititals.iOS/Editor/UAToolkitURLScheme.cs` | Create | iOS |
| 6 | `UA.Toolkit.Intersititals.Android/InstalledAppsChecker.cs` | Create | Both |

---

## Key Constraints

- **Android**: No limit on `<queries>` entries. Requires SDK version bump to add new games.
- **iOS**: Max 50 URL schemes in `LSApplicationQueriesSchemes`. Requires SDK version bump to add new games. Each target game must also ship with the SDK (or manually register its URL scheme).
- **Both platforms**: The identifier lists are baked at build time. New games require an SDK update.

---

## Verification

- **Android**: Build AAR, deploy to device, verify correct detection of installed vs not-installed test packages
- **iOS**: Build Xcode project, verify `Info.plist` contains `LSApplicationQueriesSchemes` and `CFBundleURLTypes`, test `canOpenURL` on device
- **Editor**: `GetInstalledGames` returns empty list (no native APIs available)
