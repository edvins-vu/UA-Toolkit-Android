package com.ua.toolkit;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * Utility class for opening tracker/redirect URLs directly to the Play Store.
 * Resolves URL redirects using a headless WebView and opens the store without browser.
 * Falls back to browser if store link cannot be resolved.
 *
 * Call from Unity via JNI:
 * UAStoreLauncher.openLink(context, "https://app.adjust.com/...", callback);
 */
public class UAStoreLauncher
{
    private static final String TAG = "UAStoreLauncher";
    private static final long DEFAULT_TIMEOUT_MS = 15000;
    public static final String BROWSER_FALLBACK = "browser-fallback";

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
        Log.d(TAG, "Context type: " + context.getClass().getName() +
              ", isActivity: " + (context instanceof android.app.Activity));

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
                    // Store opener failed - try browser fallback
                    Log.w(TAG, "Store open failed: " + result.message + ". Trying browser fallback.");

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
                Log.w(TAG, "URL resolution failed: " + reason + ". Opening in browser as fallback.");
                currentResolver = null;

                // Fallback: open original URL in browser
                boolean browserOpened = openInBrowser(context, url);

                if (callback != null)
                {
                    if (browserOpened)
                    {
                        // Link was opened (in browser), report as success with fallback marker
                        callback.onSuccess(BROWSER_FALLBACK);
                    }
                    else
                    {
                        // Couldn't open browser either
                        callback.onFailed(reason + " (browser fallback also failed)");
                    }
                }
            }
        });
    }

    /**
     * Open URL in default browser as fallback.
     *
     * @param context Android context
     * @param url     URL to open
     * @return true if browser was opened successfully
     */
    private static boolean openInBrowser(Context context, String url)
    {
        try
        {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

            // Only add NEW_TASK flag if not starting from an Activity
            if (!(context instanceof android.app.Activity))
            {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }

            context.startActivity(intent);
            Log.d(TAG, "Opened URL in browser: " + url);
            return true;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Failed to open browser: " + e.getMessage());
            return false;
        }
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
