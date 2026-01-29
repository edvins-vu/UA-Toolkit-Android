package com.ua.toolkit;

/**
 * Interface for receiving ad state callbacks in Unity.
 * This is implemented by a C# Proxy class.
 */
public interface AdCallback {
    void onAdStarted();
    void onAdClicked();
    void onAdFinished(boolean success);
    void onAdFailed(String reason);
}