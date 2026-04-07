package com.ua.toolkit;

import android.webkit.JavascriptInterface;
import java.lang.ref.WeakReference;

public class AdJsBridge {
    private final WeakReference<AdActivity> activityRef;

    public AdJsBridge(AdActivity activity) {
        this.activityRef = new WeakReference<>(activity);
    }

    @JavascriptInterface
    public void openStore() {
        AdActivity activity = activityRef.get();
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(activity::handleStoreRedirect);
    }
}
