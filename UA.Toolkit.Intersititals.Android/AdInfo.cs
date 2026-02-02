namespace Estoty.AdService.Core.Android
{
	/// <summary>
	/// Information about a loaded/displayed ad
	/// </summary>
	public sealed class AdInfo
	{
		public string AdUnitId { get; set; }
		public string Placement { get; set; }
		public string CreativeId { get; set; }
		public double Revenue { get; set; }
		public string NetworkName { get; set; }
		public string NetworkPlacement { get; set; }

		public AdInfo(
			string adUnitId, 
			string placement = null, 
			string creativeId = null, 
			double revenue = 0, 
			string networkName = null, 
			string networkPlacement = null)
		{
			AdUnitId = adUnitId;
			Placement = placement ?? string.Empty;
			CreativeId = creativeId ?? string.Empty;
			Revenue = revenue;
			NetworkName = networkName ?? "UAToolkit";
			NetworkPlacement = networkPlacement ?? string.Empty;
		}
	}
}
