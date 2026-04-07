# iOS Delta Changes
Changes made to the Android plugin after the iOS implementation plan was written.
Each entry must be ported to the iOS equivalent files when iOS implementation begins.

---

## DELTA-01 — Skip button no longer auto-promotes to close button on countdown complete

**Android files changed:**
- `AdUIManager.java` — added `isSkipButtonVisible()` helper
- `AdActivity.java` — `onCountdownComplete()`: only calls `showCloseButton()` if skip button is not currently visible

**Old behavior:** When `closeButtonDelay` elapsed, `showCloseButton()` was always called, hiding the skip button and showing close regardless of whether skip was visible.

**New behavior:** If the skip button is already visible when countdown completes, leave it — the user taps skip to promote to close. Close only auto-shows when skip never appeared (disabled, or `skipButtonDelaySec >= closeButtonDelay`).

**iOS port notes:**
- Add equivalent of `isSkipButtonVisible()` check in the countdown-complete handler
- The skip button click handler already calls `showCloseButton()` — no change needed there
- Applies to non-rewarded flow only (skip is never shown for rewarded ads)

---

## DELTA-02 — Playable Ad format (HTML5/WebView renderer inside existing ad flow)

**Android files changed:**
- `AdJsBridge.java` — new file, `@JavascriptInterface` bridge; `openStore()` → `activity.handleStoreRedirect()`
- `AdActivity.java` — `isPlayable` flag from `IS_PLAYABLE` intent extra; WebView renderer branch; `onContentReady()` replaces `onVideoPrepared()` shared startup; `evaluateFlowBState()` uses `closeButtonEarned` for playable; mute injects JS; lifecycle routes to WebView; tap-through disabled for playable; `IS_PLAYABLE` flag from intent
- AndroidManifest.xml — `hardwareAccelerated="true"` (already present)

**C# files changed:**
- `PlayableLoadRequest.cs` — new file (HtmlUrl replaces VideoUrl, adds IsRewarded)
- `ShowPlayableAdRequest.cs` — new file (HtmlPath = local cached file path, IS_PLAYABLE flag to native)
- `AdUnitState.cs` — added `HtmlUrl` and `IsRewarded` fields; `Reset()` clears HtmlUrl
- `UANativeBridge.cs` — `ShowPlayableAd()` method targeting same AdActivity with `IS_PLAYABLE=true`; `LaunchAdActivity()` and `PutSharedAdExtras()` private helpers extracted to eliminate duplication with `ShowAd()`
- `UASDK.cs` — `AdType.Playable`; `_playableStates` dict; Playable events; `LoadPlayable()` async with HTML download; `IsPlayableReady()`; `ShowPlayable()`; all internal handlers extended for playable path

**Key behavioral differences from video ads:**
- No reward countdown text, no "Reward earned!" overlay (AdTimerManager init'd with isRewarded=false)
- Reward condition = closeButtonDelay elapsed (not video completed)
- Flow B engagement = closeButtonEarned (not isFullyWatched)
- Background tap does NOT open popup (passes through to HTML game)
- Mute = JS injection `el.muted=true/false` on all audio/video elements
- JS bridge: `window.AdBridge.openStore()` → `handleStoreRedirect()` → `popup.openStore()`

**iOS port notes:**
- Need equivalent WKWebView setup with JavaScript message handler (WKScriptMessageHandler) instead of @JavascriptInterface
- `isPlayable` flag flows from Swift → all the same guards apply
- Mute = evaluate JS via `WKWebView.evaluateJavaScript()`
- Timer init: always `isRewarded=false` for playables
- evaluateFlowBState: use closeButtonEarned for engagement condition
- Tap-through: override hitTest in the overlay view to pass touches to WKWebView when !isPlayable
- WKWebView requires WKWebViewConfiguration with `allowsInlineMediaPlayback=true` and `mediaTypesRequiringUserActionForPlayback=[]` (equivalent to setMediaPlaybackRequiresUserGesture=false)
- Load from local path: `WKWebView.loadFileURL(URL(fileURLWithPath:), allowingReadAccessTo:)` — requires the directory containing the HTML file as `allowingReadAccessTo` parameter

---

## DELTA-03 — Playable HTML downloaded before display (local file path passed to renderer)

**C# files changed:**
- `UASDKVideoCache.cs` — added `DownloadHtmlAsync()` + `GetLocalPathForHtml()` (saves as `{adUnitId}.html`)
- `UASDK.cs` — `LoadPlayable()` now async; downloads HTML before marking ready; `ClearCache()` also deletes `*.html`; `HandleAdFailed` properly deletes cached HTML file
- `ShowPlayableAdRequest.cs` — renamed `HtmlUrl` → `HtmlPath` (now a local file path)
- `UANativeBridge.cs` — extracted `PutSharedAdExtras()` private helper + `LaunchAdActivity()` private helper; both `ShowAd()` and `ShowPlayableAd()` delegate to them (no more duplicated putExtra blocks)

**Android files changed:**
- `AdActivity.java` — removed isPlayable-specific URL validation; `config.isValid()` (file-exists check) now used for both video and playable since both are local paths

**iOS port notes:**
- `UASDK.LoadPlayable()` download is already platform-agnostic (HttpClient + persistentDataPath) — no iOS-specific change needed
- The local HTML path must be loaded via `WKWebView.loadFileURL(URL(fileURLWithPath: htmlPath), allowingReadAccessTo: URL(fileURLWithPath: directory))` where `directory` is the parent folder of the HTML file
- The validation change in AdActivity is Android-only; the iOS equivalent view controller should also use file-existence check rather than URL prefix check

---

## DELTA-04 — DownloadHtmlAsync file handle released before File.Move

**C# files changed:**
- `UASDKVideoCache.cs` — `DownloadHtmlAsync`: wrapped `HttpResponseMessage`, `Stream`, and `FileStream` inside an explicit `using (HttpResponseMessage response = ...) { }` block so all handles are disposed before `PromoteTempFile` calls `File.Move`. C# 8 `using` declarations without braces dispose at end of the enclosing scope (the whole `try {}`) — calling `File.Move` inside that scope held the file open, causing "file being used by another process" on Windows.

**iOS port notes:**
- iOS download code uses `URLSession` / `FileManager.moveItem` — file handles are managed differently and this specific Windows locking issue does not apply. No change needed on iOS.
