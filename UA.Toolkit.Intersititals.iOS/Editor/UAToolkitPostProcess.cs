#if UNITY_IOS
using System.IO;
using UnityEditor;
using UnityEditor.Callbacks;
using UnityEditor.iOS.Xcode;

namespace Estoty.AdService.Core.iOS.Editor
{
	public static class UAToolkitPostProcess
	{
		// URL schemes for your games — update this list with each SDK release
		// Max 50 entries allowed by Apple
		// Convention: derive from bundle ID by dropping first segment and joining with hyphens
		// Example: com.yourstudio.game1 -> yourstudio-game1
		private static readonly string[] GameSchemes = new[]
		{
			"yourstudio-game1",
			"yourstudio-game2",
			"yourstudio-game3",
		};

		[PostProcessBuild(order = 0)]
		public static void OnPostProcessBuild(BuildTarget target, string path)
		{
			if (target != BuildTarget.iOS) return;

			string plistPath = Path.Combine(path, "Info.plist");
			PlistDocument plist = new PlistDocument();
			plist.ReadFromFile(plistPath);

			// Add LSApplicationQueriesSchemes (schemes this app is allowed to query)
			PlistElementArray queriesArray = plist.root.CreateArray("LSApplicationQueriesSchemes");
			foreach (string scheme in GameSchemes)
			{
				queriesArray.AddString(scheme);
			}

			plist.WriteToFile(plistPath);
		}
	}
}
#endif
