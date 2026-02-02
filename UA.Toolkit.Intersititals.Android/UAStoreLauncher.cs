using Estoty.AdService.Plugins.Scripts;
using System;
using UnityEngine;

namespace Estoty.AdService.Core.Android
{
	public static class UAStoreLauncher
	{
		private const string JAVA_CLASS = "com.ua.toolkit.UAStoreLauncher";
		private const string CALLBACK_INTERFACE = "com.ua.toolkit.UAStoreLauncher$Callback";

		private static StoreLauncherCallback _currentCallback;

		public static void OpenLink(
			string url,
			Action onClick = null,
			Action<string> onSuccess = null,
			Action<string> onFailed = null
		)
		{
			if (string.IsNullOrEmpty(url))
			{
				onFailed?.Invoke("URL is null or empty");
				return;
			}

			onClick?.Invoke();

#if UNITY_ANDROID && !UNITY_EDITOR
			try
			{
				using AndroidJavaClass unityPlayer = new("com.unity3d.player.UnityPlayer");
				using AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");

				if (activity == null)
				{
					onFailed?.Invoke("currentActivity is null");
					return;
				}

				_currentCallback = new StoreLauncherCallback(
					(packageId) => onSuccess?.Invoke(packageId),
					(reason) => onFailed?.Invoke(reason)
				);

				using AndroidJavaClass launcherClass = new(JAVA_CLASS);
				launcherClass.CallStatic("openLink", activity, url, _currentCallback);
			}
			catch (Exception exception)
			{
				Debug.LogError($"[UAStoreLauncher] Bridge error: {exception.Message}");
				onFailed?.Invoke(exception.Message);
			}
#else
			Application.OpenURL(url);
			onSuccess?.Invoke("editor-mode");
#endif
		}

		public static void Cancel()
		{
#if UNITY_ANDROID && !UNITY_EDITOR
			try
			{
				using AndroidJavaClass launcherClass = new(JAVA_CLASS);
				launcherClass.CallStatic("cancel");
				_currentCallback = null;
			}
			catch (Exception exception)
			{
				Debug.LogWarning(exception.Message);
			}
#endif
		}

		private class StoreLauncherCallback : AndroidJavaProxy
		{
			private readonly Action<string> _onSuccess;
			private readonly Action<string> _onFailed;

			public StoreLauncherCallback(Action<string> onSuccess, Action<string> onFailed) : base(CALLBACK_INTERFACE)
			{
				_onSuccess = onSuccess;
				_onFailed = onFailed;
			}

			public void onSuccess(string packageId)
			{
				UnityMainThreadDispatcher.RunOnMainThread(() => _onSuccess?.Invoke(packageId));
			}

			public void onFailed(string reason)
			{
				UnityMainThreadDispatcher.RunOnMainThread(() => _onFailed?.Invoke(reason));
			}
		}
	}
}
