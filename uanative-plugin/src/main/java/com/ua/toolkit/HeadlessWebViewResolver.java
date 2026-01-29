package com.ua.toolkit;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Resolves Adjust tracker URLs using a hidden WebView to follow redirects.
 * Intercepts Play Store URLs in the redirect chain and extracts package info.
 */
public class HeadlessWebViewResolver
{
    private static final String TAG = "HeadlessWebViewResolver";
    private static final long DEFAULT_TIMEOUT_MS = 10000; // 10 seconds

    /**
     * Result of URL resolution containing store info
     */
    public static class StoreInfo
    {
        public final String packageId;
        public final String referrer;
        public final String originalUrl;

        public StoreInfo(String packageId, String referrer, String originalUrl)
        {
            this.packageId = packageId;
            this.referrer = referrer;
            this.originalUrl = originalUrl;
        }

        public boolean isValid()
        {
            return packageId != null && !packageId.isEmpty();
        }

        @Override
        public String toString()
        {
            return "StoreInfo{packageId='" + packageId + "', referrer='" + referrer + "'}";
        }
    }

    /**
     * Callback interface for resolution results
     */
    public interface ResolverCallback
    {
        void onStoreFound(StoreInfo storeInfo);
        void onFailed(String reason);
    }

    private final Context context;
    private final Handler mainHandler;
    private WebView webView;
    private ResolverCallback callback;
    private Runnable timeoutRunnable;
    private boolean isResolved = false;
    private long timeoutMs = DEFAULT_TIMEOUT_MS;

    public HeadlessWebViewResolver(Context context)
    {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Set custom timeout for resolution (default: 10 seconds)
     */
    public HeadlessWebViewResolver setTimeout(long timeoutMs)
    {
        this.timeoutMs = timeoutMs;
        return this;
    }

    /**
     * Resolve a tracker URL and extract Play Store info
     *
     * @param url      The Adjust tracker URL to resolve
     * @param callback Callback for success/failure
     */
    public void resolve(String url, ResolverCallback callback)
    {
        if (url == null || url.isEmpty())
        {
            callback.onFailed("URL is null or empty");
            return;
        }

        this.callback = callback;
        this.isResolved = false;

        // Must run on main thread for WebView
        mainHandler.post(() -> {
            try
            {
                createAndLoadWebView(url);
                startTimeout();
            }
            catch (Exception e)
            {
                Log.e(TAG, "Error creating WebView: " + e.getMessage(), e);
                notifyFailed("WebView creation failed: " + e.getMessage());
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createAndLoadWebView(String url)
    {
        Log.d(TAG, "Creating headless WebView for URL: " + url);

        webView = new WebView(context);

        // Configure WebView settings
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString() + " UAToolkit/1.0");

        // Disable caching for fresh redirects
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // Set custom WebViewClient to intercept redirects
        webView.setWebViewClient(new RedirectInterceptorClient());

        // Load the tracker URL
        webView.loadUrl(url);
    }

    /**
     * Custom WebViewClient that intercepts redirects and detects Play Store URLs
     */
    private class RedirectInterceptorClient extends WebViewClient
    {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
        {
            String url = request.getUrl().toString();
            return handleUrl(url);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url)
        {
            return handleUrl(url);
        }

        private boolean handleUrl(String url)
        {
            Log.d(TAG, "Redirect intercepted: " + url);

            // Check if this is a Play Store URL
            if (isPlayStoreUrl(url))
            {
                Log.d(TAG, "Play Store URL detected!");
                StoreInfo storeInfo = extractStoreInfo(url);

                if (storeInfo.isValid())
                {
                    notifySuccess(storeInfo);
                }
                else
                {
                    notifyFailed("Could not extract package ID from: " + url);
                }

                return true; // Cancel WebView load
            }

            // Check if this is a market:// scheme
            if (url.startsWith("market://"))
            {
                Log.d(TAG, "Market URL detected!");
                StoreInfo storeInfo = extractStoreInfo(url);

                if (storeInfo.isValid())
                {
                    notifySuccess(storeInfo);
                }
                else
                {
                    notifyFailed("Could not extract package ID from market URL: " + url);
                }

                return true; // Cancel WebView load
            }

            // Continue following redirects for other URLs
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url)
        {
            super.onPageFinished(view, url);
            Log.d(TAG, "Page finished loading: " + url);

            // If page finished loading without hitting Play Store, check the final URL
            if (!isResolved)
            {
                if (isPlayStoreUrl(url) || url.startsWith("market://"))
                {
                    StoreInfo storeInfo = extractStoreInfo(url);
                    if (storeInfo.isValid())
                    {
                        notifySuccess(storeInfo);
                        return;
                    }
                }

                // Page loaded but no Play Store URL found
                notifyFailed("Redirect chain ended without Play Store URL. Final URL: " + url);
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
        {
            Log.e(TAG, "WebView error: " + description + " for URL: " + failingUrl);
            // Don't immediately fail - the redirect might still work
        }
    }

    /**
     * Check if URL is a Play Store URL
     */
    private boolean isPlayStoreUrl(String url)
    {
        return url.contains("play.google.com/store/apps") ||
               url.contains("market.android.com") ||
               url.startsWith("market://");
    }

    /**
     * Extract package ID and referrer from Play Store URL
     */
    private StoreInfo extractStoreInfo(String url)
    {
        String packageId = null;
        String referrer = null;

        try
        {
            Uri uri = Uri.parse(url);

            // Try to get package ID from query parameter
            packageId = uri.getQueryParameter("id");

            // Get referrer if present
            referrer = uri.getQueryParameter("referrer");

            // If no ID in query params, try to extract from path
            // Format: play.google.com/store/apps/details/com.example.app
            if (packageId == null)
            {
                String path = uri.getPath();
                if (path != null && path.contains("/details/"))
                {
                    String[] parts = path.split("/details/");
                    if (parts.length > 1)
                    {
                        packageId = parts[1].split("[?#/]")[0];
                    }
                }
            }

            // For market:// scheme: market://details?id=com.example.app
            if (packageId == null && url.startsWith("market://"))
            {
                packageId = uri.getQueryParameter("id");
            }

            Log.d(TAG, "Extracted - packageId: " + packageId + ", referrer: " + referrer);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Error parsing URL: " + e.getMessage());
        }

        return new StoreInfo(packageId, referrer, url);
    }

    private void startTimeout()
    {
        timeoutRunnable = () -> {
            if (!isResolved)
            {
                Log.w(TAG, "Resolution timed out after " + timeoutMs + "ms");
                notifyFailed("Resolution timed out");
            }
        };
        mainHandler.postDelayed(timeoutRunnable, timeoutMs);
    }

    private void cancelTimeout()
    {
        if (timeoutRunnable != null)
        {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void notifySuccess(StoreInfo storeInfo)
    {
        if (isResolved) return;
        isResolved = true;

        cancelTimeout();
        cleanup();

        Log.d(TAG, "Resolution successful: " + storeInfo);

        if (callback != null)
        {
            mainHandler.post(() -> callback.onStoreFound(storeInfo));
        }
    }

    private void notifyFailed(String reason)
    {
        if (isResolved) return;
        isResolved = true;

        cancelTimeout();
        cleanup();

        Log.e(TAG, "Resolution failed: " + reason);

        if (callback != null)
        {
            mainHandler.post(() -> callback.onFailed(reason));
        }
    }

    private void cleanup()
    {
        mainHandler.post(() -> {
            if (webView != null)
            {
                webView.stopLoading();
                webView.destroy();
                webView = null;
                Log.d(TAG, "WebView cleaned up");
            }
        });
    }

    /**
     * Cancel ongoing resolution
     */
    public void cancel()
    {
        notifyFailed("Resolution cancelled");
    }
}
