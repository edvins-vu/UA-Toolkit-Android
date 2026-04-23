package com.ua.toolkit;

import com.ua.toolkit.store.HeadlessWebViewResolver;
import com.ua.toolkit.store.StoreOpener;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * Utility class for opening tracker/redirect URLs directly to the Play Store.
 * Resolves URL redirects using a headless WebView and opens the store without browser.
 * Falls back to browser if store link cannot be resolved.
 */
public class UAStoreLauncher
{
    private static final String TAG = "UA/StoreLauncher";
    private static final long DEFAULT_TIMEOUT_MS = 15000;
    public static final String BROWSER_FALLBACK = "browser-fallback";

    public interface Callback
    {
        void onSuccess(String packageId);
        void onFailed(String reason);
    }

    private static HeadlessWebViewResolver currentResolver;

    public static void openLink(Context context, String url, Callback callback)
    {
        if (context == null)
        {
            Log.e(TAG, "Context is null");
            if (callback != null) callback.onFailed("Context is null");
            return;
        }

        if (url == null || url.isEmpty())
        {
            Log.e(TAG, "URL is null or empty");
            if (callback != null) callback.onFailed("URL is null or empty");
            return;
        }

        if (currentResolver != null)
        {
            currentResolver.cancel();
            currentResolver = null;
        }

        currentResolver = new HeadlessWebViewResolver(context);
        currentResolver.setTimeout(DEFAULT_TIMEOUT_MS);

        Log.d(TAG, "openLink: resolving click URL — " + url);

        currentResolver.resolve(url, new HeadlessWebViewResolver.ResolverCallback()
        {
            @Override
            public void onStoreFound(HeadlessWebViewResolver.StoreInfo storeInfo)
            {
                currentResolver = null;
                Log.d(TAG, "openLink: store endpoint resolved — " + storeInfo.toString());
                Log.d(TAG, "openLink: opening store for packageId=" + storeInfo.packageId);
                StoreOpener.OpenResult result = StoreOpener.openStore(context, storeInfo);

                if (result.success)
                {
                    Log.d(TAG, "openLink: store opened successfully for packageId=" + storeInfo.packageId);
                    if (callback != null) callback.onSuccess(storeInfo.packageId);
                }
                else
                {
                    boolean browserOpened = openInBrowser(context, url);
                    if (callback != null)
                    {
                        if (browserOpened)
                        {
                            callback.onSuccess(BROWSER_FALLBACK);
                        }
                        else
                        {
                            callback.onFailed(result.message + " (browser fallback also failed)");
                        }
                    }
                }
            }

            @Override
            public void onFailed(String reason)
            {
                currentResolver = null;
                boolean browserOpened = openInBrowser(context, url);

                if (callback != null)
                {
                    if (browserOpened)
                    {
                        callback.onSuccess(BROWSER_FALLBACK);
                    }
                    else
                    {
                        callback.onFailed(reason + " (browser fallback also failed)");
                    }
                }
            }
        });
    }

    private static boolean openInBrowser(Context context, String url)
    {
        try
        {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

            if (!(context instanceof android.app.Activity))
            {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            context.startActivity(intent);
            return true;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Failed to open browser: " + e.getMessage());
            return false;
        }
    }

    public interface ReferrerCallback
    {
        void onResolved(String referrer); // null if resolution failed
    }

    /**
     * Resolves an Adjust click URL and returns the referrer string extracted from the redirect chain.
     * Used by the half-sheet path: fires the click event with Adjust's servers AND extracts the
     * adjust_reftag referrer so it can be embedded in the Play Store deep-link URL for attribution.
     * Calls onResolved(null) on failure — caller should fall back to a tracker-only referrer.
     */
    public static void resolveReferrer(Context context, String clickUrl, ReferrerCallback callback)
    {
        if (context == null || clickUrl == null || clickUrl.isEmpty())
        {
            Log.w(TAG, "resolveReferrer: skipped — context or clickUrl is null/empty");
            callback.onResolved(null);
            return;
        }

        if (currentResolver != null)
        {
            currentResolver.cancel();
            currentResolver = null;
        }

        currentResolver = new HeadlessWebViewResolver(context);
        currentResolver.setTimeout(DEFAULT_TIMEOUT_MS);

        Log.d(TAG, "resolveReferrer: resolving — host=" + Uri.parse(clickUrl).getHost());

        currentResolver.resolve(clickUrl, new HeadlessWebViewResolver.ResolverCallback()
        {
            @Override
            public void onStoreFound(HeadlessWebViewResolver.StoreInfo storeInfo)
            {
                currentResolver = null;
                Log.d(TAG, "resolveReferrer: resolved — packageId=" + storeInfo.packageId + " hasAdjustReftag=" + (storeInfo.referrer != null && storeInfo.referrer.contains("adjust_reftag")));
                callback.onResolved(storeInfo.referrer);
            }

            @Override
            public void onFailed(String reason)
            {
                currentResolver = null;
                Log.w(TAG, "resolveReferrer: failed (" + reason + ") — caller will use fallback referrer");
                callback.onResolved(null);
            }
        });
    }

    public static void cancel()
    {
        if (currentResolver != null)
        {
            currentResolver.cancel();
            currentResolver = null;
        }
    }
}
