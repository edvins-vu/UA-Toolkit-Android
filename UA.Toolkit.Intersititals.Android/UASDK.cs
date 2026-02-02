using Estoty.AdService.Plugins.Scripts;
using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Http;
using System.Threading.Tasks;
using UnityEngine;

namespace Estoty.AdService.Core.Android
{

	#region Data Models

	#endregion

	/// <summary>
	/// UA SDK - Main entry point for ad management
	/// Provides MaxSdk-like API for interstitial, rewarded, and banner ads
	/// </summary>
	public static class UASDK
	{
		#region Constants

		private const string TAG = "[UASDK]";
		public const string VERSION = "2.0.0";

		#endregion

		#region State

		private static bool _isInitialized;
		private static bool _isInitializing;
		private static SdkConfiguration _configuration;
		private static readonly HttpClient _httpClient = new();

		// Ad unit state tracking
		private static readonly Dictionary<string, AdUnitState> _interstitialStates = new();
		private static readonly Dictionary<string, AdUnitState> _rewardedStates = new();
		private static readonly Dictionary<string, AdUnitState> _bannerStates = new();

		// Callbacks for pending show operations
		private static Action _pendingInterstitialOnClose;
		private static Action<bool> _pendingRewardedOnComplete;
		private static string _currentShowingAdUnitId;
		private static bool _currentAdIsRewarded;

		#endregion

		#region Initialization Events

		public static event Action<SdkConfiguration> OnSdkInitializedEvent;

		#endregion

		#region Interstitial Events

		public static event Action<string, AdInfo> OnInterstitialLoadedEvent;
		public static event Action<string, ErrorInfo> OnInterstitialLoadFailedEvent;
		public static event Action<string, AdInfo> OnInterstitialDisplayedEvent;
		public static event Action<string, ErrorInfo> OnInterstitialDisplayFailedEvent;
		public static event Action<string, AdInfo> OnInterstitialHiddenEvent;
		public static event Action<string, AdInfo> OnInterstitialClickedEvent;
		public static event Action<string, AdInfo> OnInterstitialRevenuePaidEvent;

		#endregion

		#region Rewarded Events

		public static event Action<string, AdInfo> OnRewardedLoadedEvent;
		public static event Action<string, ErrorInfo> OnRewardedLoadFailedEvent;
		public static event Action<string, AdInfo> OnRewardedDisplayedEvent;
		public static event Action<string, ErrorInfo> OnRewardedDisplayFailedEvent;
		/// <summary>
		/// Fired when rewarded ad is closed. Bool parameter indicates if reward was earned (video fully watched).
		/// </summary>
		public static event Action<string, bool, AdInfo> OnRewardedHiddenEvent;
		public static event Action<string, AdInfo> OnRewardedClickedEvent;
		public static event Action<string, AdInfo> OnRewardedRevenuePaidEvent;
		/// <summary>
		/// Fired ONLY when reward was earned (video fully watched).
		/// Use this if you only care about successful rewards.
		/// </summary>
		public static event Action<string, AdInfo> OnRewardedReceivedRewardEvent;

		#endregion

		#region Properties

		public static bool IsInitialized => _isInitialized;
		public static SdkConfiguration Configuration => _configuration;

		#endregion


		#region Initialization

		/// <summary>
		/// Initialize the SDK
		/// </summary>
		public static void Init()
		{
			if (_isInitialized)
			{
				Debug.LogWarning($"{TAG} SDK already initialized");
				return;
			}

			if (_isInitializing)
			{
				Debug.LogWarning($"{TAG} SDK initialization already in progress");
				return;
			}

			_isInitializing = true;

			try
			{
				Debug.Log($"{TAG} Initializing SDK v{VERSION}...");

				// Initialize Unity main thread dispatcher
				UnityMainThreadDispatcher.Initialize();

				// Create configuration
				_configuration = new SdkConfiguration
				{
					IsInitialized = true,
					SdkVersion = VERSION
				};

				_isInitialized = true;
				_isInitializing = false;

				Debug.Log($"{TAG} SDK initialized successfully");

				// Notify listeners
				OnSdkInitializedEvent?.Invoke(_configuration);
			}
			catch (Exception exception)
			{
				_isInitializing = false;
				Debug.LogError($"{TAG} SDK initialization failed: {exception.Message}");
				throw;
			}
		}

		/// <summary>
		/// Async initialization
		/// </summary>
		public static async Task InitAsync()
		{
			Init();
			await Task.CompletedTask;
		}

		#endregion

		#region Interstitial Methods

		/// <summary>
		/// Load an interstitial ad
		/// </summary>
		/// <param name="adUnitId">Unique identifier for the ad unit (used as asset ID)</param>
		/// <param name="videoUrl">URL of the video to download</param>
		/// <param name="clickUrl">Click-through URL (Adjust tracker link)</param>
		/// <param name="closeButtonDelay">Delay before showing close button (seconds)</param>
		public static async void LoadInterstitial(string adUnitId, string videoUrl, string clickUrl, int closeButtonDelay = 5)
		{
			EnsureInitialized();

			if (!_interstitialStates.TryGetValue(adUnitId, out AdUnitState state))
			{
				state = new AdUnitState { AdUnitId = adUnitId };
				_interstitialStates[adUnitId] = state;
			}

			if (state.IsFetching)
			{
				Debug.LogWarning($"{TAG} Interstitial {adUnitId} is already loading");
				return;
			}

			if (state.IsReady)
			{
				Debug.Log($"{TAG} Interstitial {adUnitId} is already loaded");
				OnInterstitialLoadedEvent?.Invoke(adUnitId, new AdInfo(adUnitId));
				return;
			}

			state.IsFetching = true;
			state.VideoUrl = videoUrl;
			state.ClickUrl = clickUrl;
			state.CloseButtonDelay = closeButtonDelay;

			Debug.Log($"{TAG} Loading interstitial {adUnitId}...");

			try
			{
				string localPath = await DownloadVideoAsync(videoUrl, adUnitId);

				if (string.IsNullOrEmpty(localPath))
				{
					state.IsFetching = false;
					ErrorInfo error = new(adUnitId, "Failed to download video");
					Debug.LogError($"{TAG} {error}");
					OnInterstitialLoadFailedEvent?.Invoke(adUnitId, error);
					return;
				}

				state.CachedVideoPath = localPath;
				state.IsReady = true;
				state.IsFetching = false;
				state.LastFetchTime = DateTime.UtcNow;

				Debug.Log($"{TAG} Interstitial {adUnitId} loaded successfully");
				OnInterstitialLoadedEvent?.Invoke(adUnitId, new AdInfo(adUnitId));
			}
			catch (Exception exception)
			{
				state.IsFetching = false;
				ErrorInfo error = new ErrorInfo(adUnitId, exception.Message);
				Debug.LogError($"{TAG} Failed to load interstitial {adUnitId}: {exception.Message}");
				OnInterstitialLoadFailedEvent?.Invoke(adUnitId, error);
			}
		}

		/// <summary>
		/// Check if an interstitial ad is ready to show
		/// </summary>
		public static bool IsInterstitialReady(string adUnitId)
		{
			return _interstitialStates.TryGetValue(adUnitId, out AdUnitState state) && state.IsReady && !state.IsShowing;
		}

		/// <summary>
		/// Show an interstitial ad
		/// </summary>
		/// <param name="adUnitId">Ad unit ID to show</param>
		/// <param name="placement">Placement name for analytics</param>
		/// <param name="onClose">Callback when ad is closed</param>
		/// <returns>True if ad will be shown, false if not ready</returns>
		public static bool ShowInterstitial(string adUnitId, string placement = null, Action onClose = null)
		{
			EnsureInitialized();

			if (!_interstitialStates.TryGetValue(adUnitId, out AdUnitState state))
			{
				Debug.LogError($"{TAG} Interstitial {adUnitId} not found. Call LoadInterstitial first.");
				ErrorInfo error = new(adUnitId, "Ad unit not loaded");
				OnInterstitialDisplayFailedEvent?.Invoke(adUnitId, error);
				return false;
			}

			if (!state.IsReady)
			{
				Debug.LogError($"{TAG} Interstitial {adUnitId} is not ready");
				ErrorInfo error = new(adUnitId, "Ad not ready");
				OnInterstitialDisplayFailedEvent?.Invoke(adUnitId, error);
				return false;
			}

			if (state.IsShowing)
			{
				Debug.LogWarning($"{TAG} Interstitial {adUnitId} is already showing");
				return false;
			}

			state.IsShowing = true;
			_currentShowingAdUnitId = adUnitId;
			_currentAdIsRewarded = false;
			_pendingInterstitialOnClose = onClose;

			Debug.Log($"{TAG} Showing interstitial {adUnitId} at placement: {placement ?? "default"}");

#if UNITY_EDITOR
			SimulateAdInEditor(adUnitId, placement, false);
#else
			UANativeBridge.ShowAd(
				state.CachedVideoPath,
				state.ClickUrl,
				false, // isRewarded = false for interstitial
				state.CloseButtonDelay,
				(success) => HandleAdCompleted(adUnitId, placement, false, success)
			);
#endif

			state.LastShowTime = DateTime.UtcNow;
			// Note: OnInterstitialDisplayedEvent is fired from HandleAdStarted() when native confirms display

			return true;
		}

		#endregion

		#region Rewarded Methods

		/// <summary>
		/// Load a rewarded ad
		/// </summary>
		/// <param name="adUnitId">Unique identifier for the ad unit</param>
		/// <param name="videoUrl">URL of the video to download</param>
		/// <param name="clickUrl">Click-through URL (Adjust tracker link)</param>
		/// <param name="closeButtonDelay">Delay before showing close button (not used for rewarded - waits for video completion)</param>
		public static async void LoadRewarded(string adUnitId, string videoUrl, string clickUrl, int closeButtonDelay = 5)
		{
			EnsureInitialized();

			if (!_rewardedStates.TryGetValue(adUnitId, out AdUnitState state))
			{
				state = new AdUnitState { AdUnitId = adUnitId };
				_rewardedStates[adUnitId] = state;
			}

			if (state.IsFetching)
			{
				Debug.LogWarning($"{TAG} Rewarded {adUnitId} is already loading");
				return;
			}

			if (state.IsReady)
			{
				Debug.Log($"{TAG} Rewarded {adUnitId} is already loaded");
				OnRewardedLoadedEvent?.Invoke(adUnitId, new AdInfo(adUnitId));
				return;
			}

			state.IsFetching = true;
			state.VideoUrl = videoUrl;
			state.ClickUrl = clickUrl;
			state.CloseButtonDelay = closeButtonDelay;

			Debug.Log($"{TAG} Loading rewarded {adUnitId}...");

			try
			{
				string localPath = await DownloadVideoAsync(videoUrl, adUnitId);

				if (string.IsNullOrEmpty(localPath))
				{
					state.IsFetching = false;
					ErrorInfo error = new(adUnitId, "Failed to download video");
					Debug.LogError($"{TAG} {error}");
					OnRewardedLoadFailedEvent?.Invoke(adUnitId, error);
					return;
				}

				state.CachedVideoPath = localPath;
				state.IsReady = true;
				state.IsFetching = false;
				state.LastFetchTime = DateTime.UtcNow;

				Debug.Log($"{TAG} Rewarded {adUnitId} loaded successfully");
				OnRewardedLoadedEvent?.Invoke(adUnitId, new AdInfo(adUnitId));
			}
			catch (Exception excetpion)
			{
				state.IsFetching = false;
				ErrorInfo error = new(adUnitId, excetpion.Message);
				Debug.LogError($"{TAG} Failed to load rewarded {adUnitId}: {excetpion.Message}");
				OnRewardedLoadFailedEvent?.Invoke(adUnitId, error);
			}
		}

		/// <summary>
		/// Check if a rewarded ad is ready to show
		/// </summary>
		public static bool IsRewardedReady(string adUnitId)
		{
			return _rewardedStates.TryGetValue(adUnitId, out AdUnitState state) && state.IsReady && !state.IsShowing;
		}

		/// <summary>
		/// Show a rewarded ad
		/// </summary>
		/// <param name="adUnitId">Ad unit ID to show</param>
		/// <param name="placement">Placement name for analytics</param>
		/// <param name="onRewarded">Callback when ad completes (true = reward earned, false = skipped/failed)</param>
		/// <returns>True if ad will be shown, false if not ready</returns>
		public static bool ShowRewarded(string adUnitId, string placement = null, Action<bool> onRewarded = null)
		{
			EnsureInitialized();

			if (!_rewardedStates.TryGetValue(adUnitId, out AdUnitState state))
			{
				Debug.LogError($"{TAG} Rewarded {adUnitId} not found. Call LoadRewarded first.");
				ErrorInfo error = new(adUnitId, "Ad unit not loaded");
				OnRewardedDisplayFailedEvent?.Invoke(adUnitId, error);
				onRewarded?.Invoke(false);
				return false;
			}

			if (!state.IsReady)
			{
				Debug.LogError($"{TAG} Rewarded {adUnitId} is not ready");
				ErrorInfo error = new(adUnitId, "Ad not ready");
				OnRewardedDisplayFailedEvent?.Invoke(adUnitId, error);
				onRewarded?.Invoke(false);
				return false;
			}

			if (state.IsShowing)
			{
				Debug.LogWarning($"{TAG} Rewarded {adUnitId} is already showing");
				return false;
			}

			state.IsShowing = true;
			_currentShowingAdUnitId = adUnitId;
			_currentAdIsRewarded = true;
			_pendingRewardedOnComplete = onRewarded;

			Debug.Log($"{TAG} Showing rewarded {adUnitId} at placement: {placement ?? "default"}");

#if UNITY_EDITOR
			SimulateAdInEditor(adUnitId, placement, true);
#else
			UANativeBridge.ShowAd(
				state.CachedVideoPath,
				state.ClickUrl,
				true, // isRewarded = true
				state.CloseButtonDelay,
				(success) => HandleAdCompleted(adUnitId, placement, true, success)
			);
#endif

			state.LastShowTime = DateTime.UtcNow;
			// Note: OnRewardedDisplayedEvent is fired from HandleAdStarted() when native confirms display

			return true;
		}

		#endregion

		#region Utility Methods

		/// <summary>
		/// Check if any ad is currently showing
		/// </summary>
		public static bool IsAdShowing()
		{
			foreach (AdUnitState state in _interstitialStates.Values)
			{
				if (state.IsShowing)
				{
					return true;
				}
			}
			foreach (AdUnitState state in _rewardedStates.Values)
			{
				if (state.IsShowing)
				{
					return true;
				}
			}
			return false;
		}

		/// <summary>
		/// Dismiss the currently showing ad
		/// </summary>
		public static void DismissCurrentAd()
		{
#if UNITY_EDITOR
			if (!string.IsNullOrEmpty(_currentShowingAdUnitId))
			{
				HandleAdCompleted(_currentShowingAdUnitId, null, _currentAdIsRewarded, false);
			}
#else
			UANativeBridge.DismissAd();
#endif
		}

		/// <summary>
		/// Clear all cached ad videos
		/// </summary>
		public static void ClearCache()
		{
			try
			{
				string cacheDir = Application.persistentDataPath;
				string[] files = Directory.GetFiles(cacheDir, "*.mp4");

				foreach (string file in files)
				{
					File.Delete(file);
				}

				// Reset all states
				foreach (AdUnitState state in _interstitialStates.Values)
				{
					state.Reset();
				}
				foreach (AdUnitState state in _rewardedStates.Values)
				{
					state.Reset();
				}

				Debug.Log($"{TAG} Cleared {files.Length} cached videos");
			}
			catch (Exception exception)
			{
				Debug.LogError($"{TAG} Clear cache error: {exception.Message}");
			}
		}

		/// <summary>
		/// Reset SDK state (useful for domain reload in Unity Editor)
		/// </summary>
		public static void Reset()
		{
			_isInitialized = false;
			_isInitializing = false;
			_configuration = null;
			_currentShowingAdUnitId = null;
			_pendingInterstitialOnClose = null;
			_pendingRewardedOnComplete = null;

			_interstitialStates.Clear();
			_rewardedStates.Clear();
			_bannerStates.Clear();

			Debug.Log($"{TAG} SDK state reset");
		}

		/// <summary>
		/// Unsubscribe all event listeners (useful for cleanup)
		/// </summary>
		public static void UnsubscribeAllEvents()
		{
			OnSdkInitializedEvent = null;

			OnInterstitialLoadedEvent = null;
			OnInterstitialLoadFailedEvent = null;
			OnInterstitialDisplayedEvent = null;
			OnInterstitialDisplayFailedEvent = null;
			OnInterstitialHiddenEvent = null;
			OnInterstitialClickedEvent = null;
			OnInterstitialRevenuePaidEvent = null;

			OnRewardedLoadedEvent = null;
			OnRewardedLoadFailedEvent = null;
			OnRewardedDisplayedEvent = null;
			OnRewardedDisplayFailedEvent = null;
			OnRewardedHiddenEvent = null;
			OnRewardedClickedEvent = null;
			OnRewardedRevenuePaidEvent = null;
			OnRewardedReceivedRewardEvent = null;

			Debug.Log($"{TAG} All event listeners unsubscribed");
		}

		#endregion

		#region Internal Handlers

		private static void HandleAdCompleted(string adUnitId, string placement, bool isRewarded, bool success)
		{
			Debug.Log($"{TAG} Ad completed: {adUnitId}, rewarded={isRewarded}, success={success}");

			AdInfo adInfo = new(adUnitId, placement);

			if (isRewarded)
			{
				if (_rewardedStates.TryGetValue(adUnitId, out AdUnitState state))
				{
					state.IsShowing = false;
					state.IsReady = false; // Consume the ad
				}

				// Fire reward event only if video was fully watched
				if (success)
				{
					OnRewardedReceivedRewardEvent?.Invoke(adUnitId, adInfo);
				}

				// Always fire hidden event with rewardEarned bool
				OnRewardedHiddenEvent?.Invoke(adUnitId, success, adInfo);
				_pendingRewardedOnComplete?.Invoke(success);
				_pendingRewardedOnComplete = null;
			}
			else
			{
				if (_interstitialStates.TryGetValue(adUnitId, out AdUnitState state))
				{
					state.IsShowing = false;
					state.IsReady = false; // Consume the ad
				}

				OnInterstitialHiddenEvent?.Invoke(adUnitId, adInfo);
				_pendingInterstitialOnClose?.Invoke();
				_pendingInterstitialOnClose = null;
			}

			_currentShowingAdUnitId = null;
		}

		/// <summary>
		/// Called from AdCallbackProxy when ad starts displaying
		/// </summary>
		internal static void HandleAdStarted()
		{
			if (string.IsNullOrEmpty(_currentShowingAdUnitId))
			{
				return;
			}

			Debug.Log($"{TAG} Ad started displaying: {_currentShowingAdUnitId}");

			AdInfo adInfo = new(_currentShowingAdUnitId);

			if (_currentAdIsRewarded)
			{
				OnRewardedDisplayedEvent?.Invoke(_currentShowingAdUnitId, adInfo);
			}
			else
			{
				OnInterstitialDisplayedEvent?.Invoke(_currentShowingAdUnitId, adInfo);
			}
		}

		/// <summary>
		/// Called from AdCallbackProxy when ad is clicked
		/// </summary>
		internal static void HandleAdClicked()
		{
			if (string.IsNullOrEmpty(_currentShowingAdUnitId))
			{
				return;
			}

			AdInfo adInfo = new(_currentShowingAdUnitId);

			if (_currentAdIsRewarded)
			{
				OnRewardedClickedEvent?.Invoke(_currentShowingAdUnitId, adInfo);
			}
			else
			{
				OnInterstitialClickedEvent?.Invoke(_currentShowingAdUnitId, adInfo);
			}
		}

		/// <summary>
		/// Called from AdCallbackProxy when ad fails
		/// </summary>
		internal static void HandleAdFailed(string error)
		{
			if (string.IsNullOrEmpty(_currentShowingAdUnitId))
			{
				return;
			}

			ErrorInfo errorInfo = new(_currentShowingAdUnitId, error);

			if (_currentAdIsRewarded)
			{
				if (_rewardedStates.TryGetValue(_currentShowingAdUnitId, out AdUnitState state))
				{
					state.IsShowing = false;
				}
				OnRewardedDisplayFailedEvent?.Invoke(_currentShowingAdUnitId, errorInfo);
				_pendingRewardedOnComplete?.Invoke(false);
				_pendingRewardedOnComplete = null;
			}
			else
			{
				if (_interstitialStates.TryGetValue(_currentShowingAdUnitId, out AdUnitState state))
				{
					state.IsShowing = false;
				}
				OnInterstitialDisplayFailedEvent?.Invoke(_currentShowingAdUnitId, errorInfo);
				_pendingInterstitialOnClose?.Invoke();
				_pendingInterstitialOnClose = null;
			}

			_currentShowingAdUnitId = null;
		}

		#endregion

		#region Private Helpers

		private static void EnsureInitialized()
		{
			if (!_isInitialized)
			{
				throw new InvalidOperationException($"{TAG} SDK not initialized. Call UASDK.Init() first.");
			}
		}

		private static async Task<string> DownloadVideoAsync(string videoUrl, string assetId)
		{
			string localPath = GetLocalPathForAsset(assetId);

			if (File.Exists(localPath))
			{
				Debug.Log($"{TAG} Video already cached: {localPath}");
				return localPath;
			}

			try
			{
				Debug.Log($"{TAG} Downloading video: {videoUrl}, asset: {assetId}");

				string tempPath = localPath + ".tmp";
				using (HttpResponseMessage response = await _httpClient.GetAsync(videoUrl, HttpCompletionOption.ResponseHeadersRead))
				{
					response.EnsureSuccessStatusCode();
					using Stream contentStream = await response.Content.ReadAsStreamAsync();
					using FileStream fileStream = new(tempPath, FileMode.Create, FileAccess.Write, FileShare.None, 8192, true);
					await contentStream.CopyToAsync(fileStream);
				}

				if (File.Exists(localPath))
				{
					File.Delete(localPath);
				}

				File.Move(tempPath, localPath);
				Debug.Log($"{TAG} Video downloaded: {localPath}");
				return localPath;
			}
			catch (Exception exception)
			{
				Debug.LogError($"{TAG} Download failed: {exception.Message}");
				return null;
			}
		}

		private static string GetLocalPathForAsset(string assetId)
		{
			return Path.Combine(Application.persistentDataPath, $"{assetId}.mp4");
		}

#if UNITY_EDITOR
		private static async void SimulateAdInEditor(string adUnitId, string placement, bool isRewarded)
		{
			Debug.Log($"{TAG} [EDITOR] Simulating ad: {adUnitId}");

			// Simulate ad started (displayed) after brief delay
			await Task.Delay(100);
			HandleAdStarted();

			// Simulate ad completion after watching
			await Task.Delay(isRewarded ? 5000 : 2000);
			HandleAdCompleted(adUnitId, placement, isRewarded, true);
		}
#endif

		#endregion
	}
}
