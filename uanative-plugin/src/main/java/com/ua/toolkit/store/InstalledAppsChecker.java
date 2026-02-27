package com.ua.toolkit.store;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Checks which packages from a provided list are installed on the device.
 * Used to filter ads so users don't see ads for games they already have.
 *
 * Requires package names to be declared in AndroidManifest.xml <queries> block (Android 11+).
 */
public class InstalledAppsChecker {
    private static final String TAG = "InstalledAppsChecker";

    /**
     * Checks which packages from the provided CSV list are installed on the device.
     *
     * @param context          Android context (usually currentActivity)
     * @param packageNamesCsv  Comma-separated package names to check
     * @return Comma-separated list of installed package names
     */
    public static String getInstalledPackages(Context context, String packageNamesCsv) {
        if (packageNamesCsv == null || packageNamesCsv.isEmpty()) {
            return "";
        }

        PackageManager pm = context.getPackageManager();
        String[] packages = packageNamesCsv.split(",");
        StringBuilder installed = new StringBuilder();

        for (String packageName : packages) {
            String trimmed = packageName.trim();
            if (trimmed.isEmpty()) continue;

            try {
                pm.getPackageInfo(trimmed, 0);
                if (installed.length() > 0) {
                    installed.append(",");
                }
                installed.append(trimmed);
                Log.d(TAG, "Installed: " + trimmed);
            } catch (PackageManager.NameNotFoundException e) {
                Log.d(TAG, "Not installed: " + trimmed);
            }
        }

        return installed.toString();
    }
}
