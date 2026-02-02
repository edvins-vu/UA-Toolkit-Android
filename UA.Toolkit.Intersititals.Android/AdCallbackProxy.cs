using UnityEngine;
using Estoty.AdService.Plugins.Scripts;
using System;

namespace Estoty.AdService.Core.Android
{
	public class AdCallbackProxy : AndroidJavaProxy
	{
		private Action<bool> onResult;
		private const string INTERFACE_NAME = "com.ua.toolkit.AdCallback";

		public AdCallbackProxy(Action<bool> callbackAction) : base(INTERFACE_NAME)
		{
			onResult = callbackAction;
			Debug.Log($"[AdCallbackProxy] Created with interface: {INTERFACE_NAME}");
		}

		public void onAdStarted()
		{
			Debug.Log("[AdCallbackProxy] onAdStarted called from Java!");
			try
			{
				UnityMainThreadDispatcher.RunOnMainThread(() =>
				{
					Debug.Log("[AdCallbackProxy] Executing HandleAdStarted on main thread");
					UASDK.HandleAdStarted();
				});
			}
			catch (Exception exception)
			{
				Debug.LogError($"[AdCallbackProxy] Error in onAdStarted: {exception.Message}");
			}
		}

		public void onAdClicked()
		{
			Debug.Log("[AdCallbackProxy] onAdClicked called from Java!");
			try
			{
				UnityMainThreadDispatcher.RunOnMainThread(() =>
				{
					Debug.Log("[AdCallbackProxy] Executing HandleAdClicked on main thread");
					UASDK.HandleAdClicked();
				});
			}
			catch (Exception exception)
			{
				Debug.LogError($"[AdCallbackProxy] Error in onAdClicked: {exception.Message}");
			}
		}

		public void onAdFinished(bool success)
		{
			Debug.Log($"[AdCallbackProxy] onAdFinished called from Java! success={success}");
			try
			{
				UnityMainThreadDispatcher.RunOnMainThread(() =>
				{
					Debug.Log($"[AdCallbackProxy] Executing onResult on main thread, success={success}");
					onResult?.Invoke(success);
				});
			}
			catch (Exception exception)
			{
				Debug.LogError($"[AdCallbackProxy] Error in onAdFinished: {exception.Message}");
			}
		}

		public void onAdFailed(string error)
		{
			Debug.Log($"[AdCallbackProxy] onAdFailed called from Java! error={error}");
			try
			{
				UnityMainThreadDispatcher.RunOnMainThread(() =>
				{
					Debug.Log("[AdCallbackProxy] Executing HandleAdFailed on main thread");
					UASDK.HandleAdFailed(error);
				});
			}
			catch (Exception exception)
			{
				Debug.LogError($"[AdCallbackProxy] Error in onAdFailed: {exception.Message}");
			}
		}
	}
}