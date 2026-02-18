#if UNITY_IOS
using System.IO;
using UnityEditor;
using UnityEditor.Callbacks;
using UnityEditor.iOS.Xcode;
using UnityEngine;

namespace Estoty.AdService.Core.iOS.Editor
{
	/// <summary>
	/// Registers the current game's own URL scheme in CFBundleURLTypes
	/// so that other SDK-integrated games can detect it as installed.
	///
	/// Scheme naming convention:
	///   Bundle ID: com.yourstudio.game1
	///   URL Scheme: yourstudio-game1 (drops first segment, joins with hyphens)
	/// </summary>
	public static class UAToolkitURLScheme
	{
		[PostProcessBuild(order = 1)]
		public static void RegisterURLScheme(BuildTarget target, string path)
		{
			if (target != BuildTarget.iOS) return;

			string plistPath = Path.Combine(path, "Info.plist");
			PlistDocument plist = new PlistDocument();
			plist.ReadFromFile(plistPath);

			// Derive scheme from bundle identifier
			string bundleId = PlayerSettings.applicationIdentifier;
			string scheme = DeriveSchemeFromBundleId(bundleId);

			if (string.IsNullOrEmpty(scheme))
			{
				Debug.LogWarning("[UAToolkit] Could not derive URL scheme from bundle ID: " + bundleId);
				return;
			}

			// Add CFBundleURLTypes so other apps can detect this game
			PlistElementArray urlTypes = plist.root["CFBundleURLTypes"] as PlistElementArray;
			if (urlTypes == null)
			{
				urlTypes = plist.root.CreateArray("CFBundleURLTypes");
			}

			PlistElementDict urlTypeDict = urlTypes.AddDict();
			urlTypeDict.SetString("CFBundleURLName", bundleId);
			PlistElementArray urlSchemes = urlTypeDict.CreateArray("CFBundleURLSchemes");
			urlSchemes.AddString(scheme);

			plist.WriteToFile(plistPath);
			Debug.Log("[UAToolkit] Registered URL scheme: " + scheme);
		}

		/// <summary>
		/// Derives a URL scheme from a bundle ID.
		/// Example: "com.yourstudio.game1" -> "yourstudio-game1"
		/// </summary>
		private static string DeriveSchemeFromBundleId(string bundleId)
		{
			if (string.IsNullOrEmpty(bundleId)) return null;

			string[] parts = bundleId.Split('.');
			if (parts.Length < 2) return bundleId;

			// Skip first part (com/org), join rest with hyphens
			return string.Join("-", parts, 1, parts.Length - 1).ToLowerInvariant();
		}
	}
}
#endif
