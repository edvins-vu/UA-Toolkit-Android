package com.ua.toolkit;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * Opens the Google Play Store directly without using an external browser.
 * Handles fallback scenarios when Play Store app is not available.
 */
public class StoreOpener
{
    private static final String TAG = "StoreOpener";
    private static final String PLAY_STORE_PACKAGE = "com.android.vending";

    /**
     * Result of store opening attempt
     */
    public static class OpenResult
    {
        public final boolean success;
        public final String message;
        public final OpenMethod method;

        public enum OpenMethod
        {
            MARKET_INTENT,      // Opened via market:// scheme
            PLAY_STORE_WEB,     // Opened via Play Store app with HTTPS URL
            FAILED              // Could not open
        }

        private OpenResult(boolean success, String message, OpenMethod method)
        {
            this.success = success;
            this.message = message;
            this.method = method;
        }

        public static OpenResult success(OpenMethod method, String message)
        {
            return new OpenResult(true, message, method);
        }

        public static OpenResult failure(String message)
        {
            return new OpenResult(false, message, OpenMethod.FAILED);
        }
    }

    private final Context context;

    public StoreOpener(Context context)
    {
        this.context = context.getApplicationContext();
    }

    /**
     * Open Play Store for the given package ID
     *
     * @param packageId The app package ID (e.g., "com.example.app")
     * @return OpenResult indicating success/failure
     */
    public OpenResult open(String packageId)
    {
        return open(packageId, null);
    }

    /**
     * Open Play Store for the given package ID with install referrer
     *
     * @param packageId The app package ID (e.g., "com.example.app")
     * @param referrer  Optional install referrer for attribution tracking
     * @return OpenResult indicating success/failure
     */
    public OpenResult open(String packageId, String referrer)
    {
        if (packageId == null || packageId.isEmpty())
        {
            Log.e(TAG, "Package ID is null or empty");
            return OpenResult.failure("Package ID is null or empty");
        }

        Log.d(TAG, "Opening Play Store for: " + packageId + (referrer != null ? " with referrer" : ""));

        // Try market:// scheme first (fastest, most direct)
        OpenResult result = openWithMarketScheme(packageId, referrer);
        if (result.success)
        {
            return result;
        }

        // Fallback: Try Play Store app with HTTPS URL
        result = openWithPlayStoreApp(packageId, referrer);
        if (result.success)
        {
            return result;
        }

        // All methods failed
        Log.e(TAG, "All store opening methods failed for: " + packageId);
        return OpenResult.failure("Could not open Play Store. App may not be installed or enabled.");
    }

    /**
     * Open using market:// scheme with Play Store package restriction
     */
    private OpenResult openWithMarketScheme(String packageId, String referrer)
    {
        try
        {
            Uri uri = buildMarketUri(packageId, referrer);
            Log.d(TAG, "Trying market:// scheme: " + uri);

            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage(PLAY_STORE_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);

            Log.d(TAG, "Successfully opened via market:// scheme");
            return OpenResult.success(OpenResult.OpenMethod.MARKET_INTENT, "Opened via market:// scheme");
        }
        catch (ActivityNotFoundException e)
        {
            Log.w(TAG, "Market scheme failed - Play Store not found: " + e.getMessage());
            return OpenResult.failure("Play Store app not found");
        }
        catch (Exception e)
        {
            Log.e(TAG, "Market scheme failed: " + e.getMessage(), e);
            return OpenResult.failure("Market intent failed: " + e.getMessage());
        }
    }

    /**
     * Open using HTTPS URL with Play Store package restriction
     */
    private OpenResult openWithPlayStoreApp(String packageId, String referrer)
    {
        try
        {
            Uri uri = buildPlayStoreWebUri(packageId, referrer);
            Log.d(TAG, "Trying Play Store HTTPS URL: " + uri);

            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage(PLAY_STORE_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);

            Log.d(TAG, "Successfully opened via Play Store HTTPS");
            return OpenResult.success(OpenResult.OpenMethod.PLAY_STORE_WEB, "Opened via Play Store HTTPS");
        }
        catch (ActivityNotFoundException e)
        {
            Log.w(TAG, "Play Store HTTPS failed - app not found: " + e.getMessage());
            return OpenResult.failure("Play Store app not available");
        }
        catch (Exception e)
        {
            Log.e(TAG, "Play Store HTTPS failed: " + e.getMessage(), e);
            return OpenResult.failure("Play Store intent failed: " + e.getMessage());
        }
    }

    /**
     * Build market:// URI with Adjust-compatible referrer
     */
    private Uri buildMarketUri(String packageId, String referrer) {
        // Standard: market://details?id=com.package&referrer=adjust_tracker%3Dabc123
        Uri.Builder builder = new Uri.Builder()
                .scheme("market")
                .authority("details")
                .appendQueryParameter("id", packageId);

        if (referrer != null && !referrer.isEmpty()) {
            // Adjust requires the referrer string to be passed as a single query value
            builder.appendQueryParameter("referrer", referrer);
        }

        return builder.build();
    }

    /**
     * Build Play Store HTTPS URI (Fallback)
     */
    private Uri buildPlayStoreWebUri(String packageId, String referrer) {
        Uri.Builder builder = Uri.parse("https://play.google.com/store/apps/details")
                .buildUpon()
                .appendQueryParameter("id", packageId);

        if (referrer != null && !referrer.isEmpty()) {
            builder.appendQueryParameter("referrer", referrer);
        }

        return builder.build();
    }

    /**
     * Check if Play Store app is available on the device
     */
    public boolean isPlayStoreAvailable()
    {
        try
        {
            context.getPackageManager().getPackageInfo(PLAY_STORE_PACKAGE, 0);
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * Static convenience method for one-shot store opening
     */
    public static OpenResult openStore(Context context, String packageId, String referrer)
    {
        return new StoreOpener(context).open(packageId, referrer);
    }

    /**
     * Static convenience method using StoreInfo from HeadlessWebViewResolver
     */
    public static OpenResult openStore(Context context, HeadlessWebViewResolver.StoreInfo storeInfo)
    {
        if (storeInfo == null || !storeInfo.isValid())
        {
            return OpenResult.failure("Invalid store info");
        }
        return new StoreOpener(context).open(storeInfo.packageId, storeInfo.referrer);
    }
}
