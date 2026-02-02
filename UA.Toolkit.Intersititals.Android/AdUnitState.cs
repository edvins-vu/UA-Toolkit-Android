using System;

namespace Estoty.AdService.Core.Android
{
	/// <summary>
	/// Internal state tracking for an ad unit
	/// </summary>
	internal sealed class AdUnitState
	{
		public string AdUnitId { get; set; }
		public bool IsFetching { get; set; }
		public bool IsReady { get; set; }
		public bool IsShowing { get; set; }
		public DateTime? LastFetchTime { get; set; }
		public DateTime? LastShowTime { get; set; }
		public string CachedVideoPath { get; set; }
		public string ClickUrl { get; set; }
		public string VideoUrl { get; set; }
		public int CloseButtonDelay { get; set; } = 5;

		public void Reset()
		{
			IsFetching = false;
			IsReady = false;
			IsShowing = false;
			CachedVideoPath = null;
		}
	}
}
