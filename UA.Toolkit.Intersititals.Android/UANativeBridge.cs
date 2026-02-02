using System;
using UnityEngine;

namespace Estoty.AdService.Core.Android
{
	public static class UANativeBridge
	{
		private const string ACTIVITY_CLASS_NAME = "com.ua.toolkit.AdActivity";

		private static AdCallbackProxy _currentProxy;

		public static void ShowAd(
			string videoPath, 
			string clickUrl, 
			bool isRewarded, 
			int closeButtonDelay, 
			Action<bool> resultCallback
		)
		{
#if UNITY_ANDROID && !UNITY_EDITOR
			try
			{
				Debug.Log($"[UANativeBridge] ShowAd called. videoPath={videoPath}, isRewarded={isRewarded}");

				Action<bool> wrappedCallback = (success) =>
				{
					Debug.Log($"[UANativeBridge] Callback received! success={success}");
					_currentProxy = null;
					resultCallback?.Invoke(success);
				};

				_currentProxy = new AdCallbackProxy(wrappedCallback);
				Debug.Log($"[UANativeBridge] AdCallbackProxy created: {_currentProxy != null}");

				using (AndroidJavaClass activityClass = new(ACTIVITY_CLASS_NAME))
				{
					if (activityClass == null)
					{
						Debug.LogError("[UANativeBridge] Failed to get AdActivity class!");
						resultCallback?.Invoke(false);
						return;
					}

					// Set the callback - this converts AndroidJavaProxy to Java object
					activityClass.SetStatic("callback", _currentProxy);
					Debug.Log("[UANativeBridge] Callback set on AdActivity");

					try
					{
						AndroidJavaObject callbackCheck = activityClass.GetStatic<AndroidJavaObject>("callback");
						Debug.Log($"[UANativeBridge] Callback verification: {(callbackCheck != null ? "SET" : "NULL")}");
					}
					catch (Exception exception)
					{
						Debug.LogWarning($"[UANativeBridge] Could not verify callback: {exception.Message}");
					}
				}

				using AndroidJavaClass unityPlayer = new("com.unity3d.player.UnityPlayer");
				AndroidJavaObject currentActivity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");

				if (currentActivity == null)
				{
					Debug.LogError("[UANativeBridge] currentActivity is null!");
					resultCallback?.Invoke(false);
					return;
				}

				using AndroidJavaObject intent = new("android.content.Intent");
				intent.Call<AndroidJavaObject>("setClassName", currentActivity, ACTIVITY_CLASS_NAME);
				intent.Call<AndroidJavaObject>("putExtra", "VIDEO_PATH", videoPath);
				intent.Call<AndroidJavaObject>("putExtra", "CLICK_URL", clickUrl ?? "");
				intent.Call<AndroidJavaObject>("putExtra", "IS_REWARDED", isRewarded);
				intent.Call<AndroidJavaObject>("putExtra", "CLOSE_BUTTON_DELAY", closeButtonDelay);

				Debug.Log("[UANativeBridge] Starting AdActivity...");
				currentActivity.Call("startActivity", intent);
				Debug.Log("[UANativeBridge] AdActivity started");
			}
			catch (Exception exception)
			{
				Debug.LogError($"[UANativeBridge] Error: {exception.Message}\n{exception.StackTrace}");
				_currentProxy = null;
				resultCallback?.Invoke(false);
			}
#else
			Debug.Log("[UANativeBridge] Editor mode - simulating success");
			resultCallback?.Invoke(true);
#endif
		}

		public static void DismissAd()
		{
#if UNITY_ANDROID && !UNITY_EDITOR
              try
              {
                  using AndroidJavaClass activityClass = new(ACTIVITY_CLASS_NAME);
                  activityClass.CallStatic("dismissAd");
              }
              catch (Exception exception)
              {
                  Debug.LogError($"[UANativeBridge] Dismiss Error: {exception.Message}");
              }
#endif
		}
	}
}