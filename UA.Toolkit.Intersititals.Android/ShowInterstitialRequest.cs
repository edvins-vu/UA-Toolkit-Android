using System;

namespace Estoty.AdService.Requests
{
	public readonly struct ShowInterstitialRequest
	{
		public string Url { get; }
		public string AssetId { get; }
		public string ClickUrl { get; }
		public string AdjustToken { get; }
		public bool IsRewarded { get; }
		public int CloseButtonDelay { get; }
		public string Placement { get; }

		public ShowInterstitialRequest(
			string url, 
			string assetId,
			string clickUrl, 
			string adjustToken, 
			bool isRewarded, 
			int closeButtonDelay,
			string placement
		)
		{
			Validate(url, assetId, clickUrl, adjustToken, closeButtonDelay, placement);	

			Url = url;
			AssetId = assetId;
			ClickUrl = clickUrl;
			AdjustToken = adjustToken;
			IsRewarded = isRewarded;
			CloseButtonDelay = closeButtonDelay;
			Placement = placement;
		}

		private static void Validate(
			string url,
			string assetId,
			string clickUrl, 
			string adjustToken, 
			int closeButtonDelay,
			string placement
		)
		{
			if (string.IsNullOrEmpty(url))
			{
				throw new ArgumentException("Video url cannot be null or empty", nameof(url));
			}

			if (string.IsNullOrEmpty(assetId))
			{
				throw new ArgumentException("Asset ID cannot be null or empty", nameof(assetId));
			}

			if (string.IsNullOrEmpty(clickUrl))
			{
				
				throw new ArgumentException("Click URL cannot be null or empty", nameof(clickUrl));
			}
			if (string.IsNullOrEmpty(adjustToken))
			{
				throw new ArgumentException("Adjust token cannot be null or empty", nameof(adjustToken));
			}

			if (closeButtonDelay < 0)
			{
				throw new ArgumentOutOfRangeException(nameof(closeButtonDelay), "Close button delay cannot be negative");
			}

			if (string.IsNullOrEmpty(placement))
			{
				throw new ArgumentException("Placement cannot be null or empty", nameof(placement));
			}
		}
	}
}
