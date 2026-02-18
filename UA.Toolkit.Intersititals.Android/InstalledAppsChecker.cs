using System.Collections.Generic;
using System.Runtime.InteropServices;
using UnityEngine;

namespace Estoty.AdService.Core
{
	public static class InstalledAppsChecker
	{
		private const string TAG = "[InstalledAppsChecker]";

#if UNITY_IOS && !UNITY_EDITOR
		[DllImport("__Internal")]
		private static extern string CheckInstalledSchemes(string schemesCsv);
#endif

		/// <summary>
		/// Checks which games from the provided list are installed on the device.
		///
		/// Android: pass package names (e.g., "com.yourstudio.game1")
		/// iOS: pass URL schemes (e.g., "yourstudio-game1")
		/// </summary>
		/// <param name="identifiers">List of package names (Android) or URL schemes (iOS)</param>
		/// <returns>List of identifiers that are installed on the device</returns>
		public static List<string> GetInstalledGames(List<string> identifiers)
		{
			if (identifiers == null || identifiers.Count == 0)
			{
				return new List<string>();
			}

			string csv = string.Join(",", identifiers);

#if UNITY_ANDROID && !UNITY_EDITOR
			return CheckAndroid(csv);
#elif UNITY_IOS && !UNITY_EDITOR
			return CheckiOS(csv);
#else
			Debug.Log($"{TAG} Editor mode - returning empty list");
			return new List<string>();
#endif
		}

#if UNITY_ANDROID && !UNITY_EDITOR
		private static List<string> CheckAndroid(string csv)
		{
			try
			{
				using AndroidJavaClass unityPlayer = new("com.unity3d.player.UnityPlayer");
				using AndroidJavaObject activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity");
				using AndroidJavaClass checker = new("com.ua.toolkit.InstalledAppsChecker");

				string result = checker.CallStatic<string>("getInstalledPackages", activity, csv);

				if (string.IsNullOrEmpty(result))
				{
					return new List<string>();
				}

				return new List<string>(result.Split(','));
			}
			catch (System.Exception e)
			{
				Debug.LogError($"{TAG} Android check failed: {e.Message}");
				return new List<string>();
			}
		}
#endif

#if UNITY_IOS && !UNITY_EDITOR
		private static List<string> CheckiOS(string csv)
		{
			try
			{
				string result = CheckInstalledSchemes(csv);

				if (string.IsNullOrEmpty(result))
				{
					return new List<string>();
				}

				return new List<string>(result.Split(','));
			}
			catch (System.Exception e)
			{
				Debug.LogError($"{TAG} iOS check failed: {e.Message}");
				return new List<string>();
			}
		}
#endif
	}
}
