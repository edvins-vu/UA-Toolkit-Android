package com.ua.toolkit;

import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;
import android.widget.VideoView;

/**
 * Manages video playback for ads.
 */
public class AdVideoPlayer
{
    private static final String TAG = "AdVideoPlayer";

    public interface Listener
    {
        void onVideoPrepared(MediaPlayer mediaPlayer);
        void onVideoCompleted();
        void onVideoError(int what, int extra);
    }

    private final VideoView videoView;
    private final Listener listener;
    private MediaPlayer mediaPlayer;
    private int savedPosition = 0;
    private String currentVideoPath;
    private boolean isSeeking = false;

    public AdVideoPlayer(VideoView videoView, Listener listener)
    {
        this.videoView = videoView;
        this.listener = listener;
    }

    public void load(String videoPath, boolean loopVideo)
    {
        currentVideoPath = videoPath;
        videoView.setVideoPath(videoPath);

        videoView.setOnPreparedListener(mp ->
        {
            Log.d(TAG, "Video prepared");
            mediaPlayer = mp;
            listener.onVideoPrepared(mp);
            videoView.start();
        });

        videoView.setOnCompletionListener(mp ->
        {
            Log.d(TAG, "Video completed");
            listener.onVideoCompleted();

            if (loopVideo)
            {
                mp.seekTo(0);
                mp.start();
            }
        });

        videoView.setOnErrorListener((mp, what, extra) ->
        {
            Log.e(TAG, "Video playback failed — path: " + currentVideoPath
                    + " | " + describeError(what, extra));
            listener.onVideoError(what, extra);
            return true;
        });
    }

    private static String describeError(int what, int extra)
    {
        String whatStr;
        switch (what)
        {
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:     whatStr = "UNKNOWN"; break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED: whatStr = "SERVER_DIED"; break;
            default:                                  whatStr = "what=" + what; break;
        }

        String extraStr;
        switch (extra)
        {
            case MediaPlayer.MEDIA_ERROR_IO:           extraStr = "IO_ERROR (missing or unreadable file)"; break;
            case MediaPlayer.MEDIA_ERROR_MALFORMED:    extraStr = "MALFORMED (corrupt or incomplete file)"; break;
            case MediaPlayer.MEDIA_ERROR_UNSUPPORTED:  extraStr = "UNSUPPORTED (codec not supported)"; break;
            case MediaPlayer.MEDIA_ERROR_TIMED_OUT:    extraStr = "TIMED_OUT"; break;
            case 200:                                  extraStr = "NOT_VALID_FOR_PROGRESSIVE_PLAYBACK"; break;
            default:                                   extraStr = "extra=" + extra; break;
        }

        return whatStr + " / " + extraStr;
    }

    public void pause()
    {
        if (videoView != null && videoView.isPlaying())
        {
            savedPosition = videoView.getCurrentPosition();
            videoView.pause();
            Log.d(TAG, "Video paused at position: " + savedPosition);
        }
    }

    public void resume()
    {
        if (videoView != null)
        {
            if (savedPosition > 0 && mediaPlayer != null)
            {
                // Use seek completion listener on API 26+ for accurate seeking
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                {
                    final int targetPosition = savedPosition;
                    isSeeking = true;
                    mediaPlayer.setOnSeekCompleteListener(mp ->
                    {
                        Log.d(TAG, "Seek completed to position: " + targetPosition);
                        mp.setOnSeekCompleteListener(null);
                        isSeeking = false;
                        videoView.start();
                    });
                    videoView.seekTo(savedPosition);
                }
                else
                {
                    videoView.seekTo(savedPosition);
                    videoView.start();
                }
                savedPosition = 0;
                Log.d(TAG, "Video resuming from position: " + savedPosition);
            }
            else
            {
                videoView.start();
                Log.d(TAG, "Video resumed");
            }
        }
    }

    public void stop()
    {
        if (videoView != null)
        {
            videoView.stopPlayback();
        }
    }

    public boolean isSeeking()
    {
        return isSeeking;
    }

    public int getCurrentPosition()
    {
        return videoView != null ? videoView.getCurrentPosition() : 0;
    }

    public int getDuration()
    {
        return videoView != null ? videoView.getDuration() : 0;
    }
}