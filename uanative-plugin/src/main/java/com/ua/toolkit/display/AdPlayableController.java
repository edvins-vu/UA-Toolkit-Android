package com.ua.toolkit.display;

import android.content.Context;
import android.util.Log;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import com.ua.toolkit.AdJsBridge;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Owns the WebView lifecycle for playable (HTML5) ads.
 * Encapsulates WebView setup, HTML injection, audio-mute/pause bridging,
 * lifecycle pause/resume, and teardown. AdActivity delegates all WebView
 * interactions here, keeping itself free of WebView internals.
 *
 * Security note on setAllowUniversalAccessFromFileURLs: this setting permits
 * any file:// page to read any other file:// path accessible to the app UID.
 * The risk is accepted because HTML content is downloaded exclusively from our
 * own ad servers, UASDKVideoCache validates Content-Length and uses atomic
 * rename, and the WebView has no internet access beyond what the HTML itself
 * initiates. Do NOT enable this for WebViews that load third-party URLs.
 */
public class AdPlayableController
{
    /** Callback interface for fatal WebView load errors. */
    public interface ErrorCallback
    {
        void onError(String message);
    }

    private static final String TAG = "UA/PlayableCtrl";

    /**
     * Injected as the first child of {@code <head>} via {@code loadDataWithBaseURL} before
     * any game scripts run. Intercepts the AudioContext constructor to track all instances,
     * patches {@code prototype.resume}, and installs {@code window.__adMute} /
     * {@code window.__adPause} for two independent audio-control flags:
     *   _m — user mute button
     *   _p — lifecycle/focus pause (phone calls, app-switch, popup expand)
     * Falls back to muting {@code <audio>}/{@code <video>} elements and Howler.js if present.
     */
    private static final String MUTE_PATCHER_JS =
        "(function(){" +
        "var _c=[],_m=false,_p=false;" +
        "var _O=window.AudioContext||window.webkitAudioContext;" +
        "if(_O){" +
        "var _r=_O.prototype.resume;" +
        "_O.prototype.resume=function(){" +
        "if(_m||_p)return Promise.resolve();" +
        "return _r.apply(this,arguments);" +
        "};" +
        "var _P=function(o){var c=new _O(o);_c.push(c);try{if(_m||_p)c.suspend();}catch(e){}return c;};" +
        "_P.prototype=_O.prototype;" +
        "window.AudioContext=window.webkitAudioContext=_P;" +
        "}" +
        "window.__adMute=function(m){" +
        "_m=m;" +
        "document.querySelectorAll('audio,video').forEach(function(el){el.muted=m||_p;});" +
        "_c.forEach(function(c){try{m?c.suspend():(_p?null:c.resume());}catch(e){}});" +
        "try{if(window.Howler)window.Howler.mute(m||_p);}catch(e){}" +
        "};" +
        "window.__adPause=function(p){" +
        "_p=p;" +
        "document.querySelectorAll('audio,video').forEach(function(el){el.muted=p||_m;});" +
        "_c.forEach(function(c){try{p?c.suspend():(_m?null:c.resume());}catch(e){}});" +
        "try{if(window.Howler)window.Howler.mute(p||_m);}catch(e){}" +
        "};" +
        "})()";

    /**
     * Safety-net fallback evaluated in {@code onPageFinished}.
     * No-ops immediately if {@link #MUTE_PATCHER_JS} was already injected via
     * {@code loadDataWithBaseURL}. Covers the edge case where content was served
     * without the head injection. Patches prototype.resume on existing AudioContext
     * instances rather than the constructor (no new instances to track at this point).
     */
    private static final String MUTE_PATCHER_FALLBACK_JS =
        "(function(){" +
        "if(typeof window.__adMute==='function')return;" +
        "var _m=false,_p=false,_O=window.AudioContext||window.webkitAudioContext;" +
        "if(_O&&!_O.prototype.__adMutePatch){" +
        "_O.prototype.__adMutePatch=true;" +
        "var _r=_O.prototype.resume;" +
        "_O.prototype.resume=function(){if(_m||_p)return Promise.resolve();return _r.apply(this,arguments);};" +
        "}" +
        "window.__adMute=function(m){" +
        "_m=m;" +
        "document.querySelectorAll('audio,video').forEach(function(el){el.muted=m||_p;});" +
        "try{if(window.Howler){window.Howler.mute(m||_p);" +
        "if(Howler.ctx)m?Howler.ctx.suspend():(_p?null:Howler.ctx.resume());}" +
        "}catch(e){}" +
        "};" +
        "window.__adPause=function(p){" +
        "_p=p;" +
        "document.querySelectorAll('audio,video').forEach(function(el){el.muted=p||_m;});" +
        "try{if(window.Howler){window.Howler.mute(p||_m);" +
        "if(Howler.ctx)p?Howler.ctx.suspend():(_m?null:Howler.ctx.resume());}" +
        "}catch(e){}" +
        "};" +
        "})()";

    private WebView webView;
    private boolean pageLoaded = false; // guards onPageFinished double-fire

    /**
     * Constructs the controller, configures the WebView, and inserts it into the root
     * layout at index 0 (behind all UIManager controls).
     *
     * @param context    Activity context for WebView construction
     * @param rootLayout Root FrameLayout — WebView is inserted at index 0
     * @param jsBridge   Pre-constructed JS bridge object
     * @param onReady    Fired when the page has loaded and the ad can start
     * @param onError    Fired with a message if the WebView fails to load
     */
    public AdPlayableController(Context context, FrameLayout rootLayout,
                                AdJsBridge jsBridge,
                                Runnable onReady,
                                ErrorCallback onError)
    {
        webView = new WebView(context);
        configureSettings(webView.getSettings());
        webView.addJavascriptInterface(jsBridge, "AdBridge");
        webView.setWebViewClient(buildWebViewClient(onReady, onError));
        FrameLayout.LayoutParams matchParent = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        rootLayout.addView(webView, 0, matchParent);
        // load() is deferred to AdActivity.startAd() so it runs after all managers and
        // the popup are fully wired up — mirrors the video path and gives the watchdog
        // a clean start point.
    }

    // --- Public API ---

    /**
     * Reads the cached HTML file, injects {@link #MUTE_PATCHER_JS} as the first child of
     * {@code <head>}, and loads the document via {@code loadDataWithBaseURL} so relative
     * sub-resource paths (JS bundles, images, CSS) resolve correctly.
     *
     * @param htmlPath Absolute path to the cached HTML file
     * @throws Exception if the file cannot be read or decoded
     */
    public void load(String htmlPath) throws Exception
    {
        File htmlFile = new File(htmlPath);
        byte[] raw = readAllBytes(new FileInputStream(htmlFile));
        String html = new String(raw, "UTF-8");
        // Inject mute patcher as first child of <head> — before any game scripts.
        // Injecting inside <head> avoids placing a <script> before <!DOCTYPE>,
        // which would trigger quirks mode in WebView.
        String scriptTag = "<script>" + MUTE_PATCHER_JS + "</script>";
        int headIdx = html.toLowerCase().indexOf("<head>");
        String patched = headIdx >= 0
            ? html.substring(0, headIdx + 6) + scriptTag + html.substring(headIdx + 6)
            : scriptTag + html; // fallback for documents with no <head>
        // Base URL = parent directory so relative sub-resource paths resolve correctly —
        // same behaviour as loadUrl("file:///...").
        String baseUrl = "file://" + htmlFile.getParent() + "/";
        Log.d(TAG, "load — loadDataWithBaseURL base=" + baseUrl);
        webView.loadDataWithBaseURL(baseUrl, patched, "text/html", "UTF-8", null);
    }

    /**
     * Signals the game to pause audio, then suspends WebView JS timers and rendering.
     * Call order matters: __adPause must fire before pauseTimers/onPause so the game's
     * own resume() calls cannot override the pause mid-frame.
     */
    public void pause()
    {
        if (webView == null) return;
        webView.evaluateJavascript("if(typeof window.__adPause==='function')window.__adPause(true);", null);
        webView.pauseTimers();
        webView.onPause();
    }

    /**
     * Resumes WebView rendering and JS timers, then signals the game to resume audio.
     * Call order mirrors {@link #pause()}: rendering must be active before the JS fires.
     */
    public void resume()
    {
        if (webView == null) return;
        webView.onResume();
        webView.resumeTimers();
        webView.evaluateJavascript("if(typeof window.__adPause==='function')window.__adPause(false);", null);
    }

    /**
     * Routes a mute-button toggle to the game via the JS bridge.
     * Used when the user taps the mute button while the game is running.
     */
    public void applyMute(boolean muted)
    {
        if (webView == null) return;
        webView.evaluateJavascript(
            "(function(m){" +
            "if(typeof window.__adMute==='function'){window.__adMute(m);}" +
            "else{document.querySelectorAll('audio,video').forEach(function(el){el.muted=m;});}" +
            "})(" + muted + ")", null);
    }

    /**
     * Re-applies the current mute state immediately after page load, in case the user
     * toggled the mute button before {@code onPageFinished} fired.
     * No-ops when {@code muted} is false — silence requires no action.
     */
    public void applyInitialMute(boolean muted)
    {
        if (webView == null || !muted) return;
        webView.evaluateJavascript(
            "if(typeof window.__adMute==='function')window.__adMute(true);" +
            "else document.querySelectorAll('audio,video').forEach(function(el){el.muted=true;});",
            null);
    }

    /**
     * Stops loading and destroys the WebView renderer. Must be called before
     * {@code Activity.finish()} so Chromium releases audio focus immediately —
     * deferring to {@code onDestroy()} leaves Chromium's focus active during the
     * slide-out animation, which can block the next ad's countdown timer.
     */
    public void destroy()
    {
        if (webView == null) return;
        webView.stopLoading();
        webView.destroy();
        webView = null;
    }

    // --- Private helpers ---

    private void configureSettings(WebSettings ws)
    {
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        // Required to load cached HTML via file:// URI on API 30+; without it the
        // WebView throws a SecurityException.
        ws.setAllowFileAccess(true);
        // Required for HTML5 games that reference sub-resources via relative paths
        // from a cached index.html — see class-level security note.
        ws.setAllowUniversalAccessFromFileURLs(true);
    }

    private WebViewClient buildWebViewClient(Runnable onReady, ErrorCallback onError)
    {
        return new WebViewClient()
        {
            @Override
            public void onPageFinished(WebView view, String url)
            {
                if (pageLoaded) return;
                pageLoaded = true;
                // Safety net: if loadDataWithBaseURL somehow served unpatched content,
                // window.__adMute will be undefined. The fallback patcher is a no-op if
                // the full patcher was already injected into <head>.
                view.evaluateJavascript(MUTE_PATCHER_FALLBACK_JS, null);
                onReady.run();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err)
            {
                if (!req.isForMainFrame()) return;
                onError.onError("WebView error: " + (err != null ? err.getDescription() : "unknown"));
            }
        };
    }

    private static byte[] readAllBytes(InputStream is) throws IOException
    {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        try {
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
        } finally {
            is.close();
        }
        return buf.toByteArray();
    }
}
