package com.ua.toolkit;

import android.content.Context;
import android.util.Log;

/**
 * Utility class for opening tracker/redirect URLs directly to the Play Store.
 * Resolves URL redirects using a headless WebView and opens the store without browser.
 *
 * Call from Unity via JNI:
 * UAStoreLauncher.openLink(context, "https://app.adjust.com/...", callback);
 */
public class UAStoreLauncher
{
    private static final String TAG = "UAStoreLauncher";
    private static final long DEFAULT_TIMEOUT_MS = 15000;

    /**
     * Callback interface for link opening results.
     * Implemented by C# AndroidJavaProxy.
     */
    public interface Callback
    {
        void onSuccess(String packageId);
        void onFailed(String reason);
    }

    private static HeadlessWebViewResolver currentResolver;

    /**
     * Open a tracker/redirect URL by resolving it and opening the Play Store directly.
     *
     * @param context  Android context (Unity activity)
     * @param url      The tracker URL to resolve (e.g., Adjust link)
     * @param callback Callback for success/failure notification (can be null)
     */
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

        Log.d(TAG, "Opening link: " + url);

        // Cancel any previous resolution in progress
        if (currentResolver != null)
        {
            currentResolver.cancel();
            currentResolver = null;
        }

        currentResolver = new HeadlessWebViewResolver(context);
        currentResolver.setTimeout(DEFAULT_TIMEOUT_MS);

        currentResolver.resolve(url, new HeadlessWebViewResolver.ResolverCallback()
        {
            @Override
            public void onStoreFound(HeadlessWebViewResolver.StoreInfo storeInfo)
            {
                Log.d(TAG, "Store info resolved: " + storeInfo);
                currentResolver = null;

                StoreOpener.OpenResult result = StoreOpener.openStore(context, storeInfo);

                if (result.success)
                {
                    Log.d(TAG, "Store opened successfully via " + result.method);
                    if (callback != null) callback.onSuccess(storeInfo.packageId);
                }
                else
                {
                    Log.e(TAG, "Store open failed: " + result.message);
                    if (callback != null) callback.onFailed(result.message);
                }
            }

            @Override
            public void onFailed(String reason)
            {
                Log.e(TAG, "URL resolution failed: " + reason);
                currentResolver = null;
                if (callback != null) callback.onFailed(reason);
            }
        });
    }

    /**
     * Cancel any ongoing URL resolution.
     */
    public static void cancel()
    {
        if (currentResolver != null)
        {
            currentResolver.cancel();
            currentResolver = null;
            Log.d(TAG, "Resolution cancelled");
        }
    }
}
