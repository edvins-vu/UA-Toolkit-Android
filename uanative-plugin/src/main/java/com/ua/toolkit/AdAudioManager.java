package com.ua.toolkit;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;

/**
 * Manages audio focus and mute state for ad playback.
 */
public class AdAudioManager
{
    private final AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private MediaPlayer mediaPlayer;
    private boolean isMuted = false;

    public AdAudioManager(Context context)
    {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
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
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        }
    }

    public void abandonFocus()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null)
        {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
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
            float volume = isMuted ? 0.0f : 1.0f;
            mediaPlayer.setVolume(volume, volume);
        }
    }

    public void release()
    {
        abandonFocus();
        mediaPlayer = null;
    }
}