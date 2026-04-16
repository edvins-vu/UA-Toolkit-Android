package com.ua.toolkit.display;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

/**
 * Manages audio focus and mute state for ad playback.
 */
public class AdAudioManager
{
    private static final String TAG = "UA/AudioManager";

    /**
     * Notified when audio focus is lost or regained by an external event
     * (notification sound, alarm, etc.) that does not trigger Activity lifecycle.
     * Phone calls trigger onPause/onResume instead and do not use this interface.
     */
    public interface FocusChangeListener
    {
        void onAudioFocusPause();
        void onAudioFocusResume();
    }

    private final AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest; // API 26+ only
    private MediaPlayer mediaPlayer;
    private boolean isMuted = false;
    private boolean isDucked = false;
    private FocusChangeListener focusChangeListener;

    private final AudioManager.OnAudioFocusChangeListener internalFocusListener = focusChange ->
    {
        switch (focusChange)
        {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                Log.d(TAG, "Audio focus lost (change=" + focusChange + ") — pausing ad");
                if (focusChangeListener != null) focusChangeListener.onAudioFocusPause();
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lower volume rather than pausing — short sounds like notifications
                Log.d(TAG, "Audio focus duck — lowering volume");
                isDucked = true;
                applyMuteState();
                break;

            case AudioManager.AUDIOFOCUS_GAIN:
                Log.d(TAG, "Audio focus gained — restoring ad audio");
                isDucked = false;
                applyMuteState(); // restores correct volume, respects mute toggle
                if (focusChangeListener != null) focusChangeListener.onAudioFocusResume();
                break;
        }
    };

    public AdAudioManager(Context context)
    {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void setFocusChangeListener(FocusChangeListener listener)
    {
        this.focusChangeListener = listener;
    }

    public void setMediaPlayer(MediaPlayer mediaPlayer)
    {
        this.mediaPlayer = mediaPlayer;
        applyMuteState();
    }

    public void requestFocus()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build())
                    .setOnAudioFocusChangeListener(internalFocusListener)
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        }
        else
        {
            //noinspection deprecation
            audioManager.requestAudioFocus(
                    internalFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
    }

    public void abandonFocus()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null)
        {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        }
        else
        {
            //noinspection deprecation
            audioManager.abandonAudioFocus(internalFocusListener);
        }
    }

    public void toggleMute()
    {
        isMuted = !isMuted;
        applyMuteState();
    }

    public boolean isMuted()
    {
        return isMuted;
    }

    private void applyMuteState()
    {
        if (mediaPlayer != null)
        {
            float volume = isDucked ? 0.2f : (isMuted ? 0.0f : 1.0f);
            // setVolume() calls native _setVolume which throws java.lang.Error (not Exception)
            // on Android 14+ when the MediaPlayer has been released at the native level but the
            // Java reference is not yet null — e.g. when a queued focus-change callback fires
            // just after finishWithResult() called audioManager.release().
            try { mediaPlayer.setVolume(volume, volume); }
            catch (Throwable t) { Log.w(TAG, "applyMuteState: setVolume failed — " + t.getMessage()); }
        }
    }

    public void release()
    {
        abandonFocus();
        mediaPlayer = null;
    }
}
