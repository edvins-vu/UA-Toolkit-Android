namespace Estoty.AdService.Core.Android
{
	/// <summary>
	/// Error information for failed ad operations
	/// </summary>
	public sealed class ErrorInfo
	{
		public int Code { get; set; }
		public string Message { get; set; }
		public string AdUnitId { get; set; }

		public ErrorInfo(string adUnitId, string message, int code = -1)
		{
			AdUnitId = adUnitId;
			Message = message;
			Code = code;
		}

		public override string ToString() => $"[{Code}] {Message}";
	}
}
