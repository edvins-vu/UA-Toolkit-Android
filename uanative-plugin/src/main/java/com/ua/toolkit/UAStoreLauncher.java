package com.ua.toolkit;

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
    private static final String TAG = "UAStoreLauncher";
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

        currentResolver.resolve(url, new HeadlessWebViewResolver.ResolverCallback()
        {
            @Override
            public void onStoreFound(HeadlessWebViewResolver.StoreInfo storeInfo)
            {
                currentResolver = null;
                StoreOpener.OpenResult result = StoreOpener.openStore(context, storeInfo);

                if (result.success)
                {
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

    public static void cancel()
    {
        if (currentResolver != null)
        {
            currentResolver.cancel();
            currentResolver = null;
        }
    }
}
