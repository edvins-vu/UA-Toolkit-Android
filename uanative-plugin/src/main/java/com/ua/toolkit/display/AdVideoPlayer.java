package com.ua.toolkit.display;

import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;
import android.widget.VideoView;

import java.io.File;

/**
 * Manages video playback for ads.
 */
public class AdVideoPlayer
{
    private static final String TAG = "UA/VideoPlayer";

    public interface Listener
    {
        void onVideoPrepared(MediaPlayer mediaPlayer);
        void onVideoCompleted();
        void onVideoError(int what, int extra);
    }

    private final VideoView videoView;
    private final Listener listener;
    private int savedPosition = 0;
    private int lastPausedPosition = 0;
    private String currentVideoPath;
    private boolean isSuspended = false;

    public AdVideoPlayer(VideoView videoView, Listener listener)
    {
        this.videoView = videoView;
        this.listener = listener;
    }

    public void load(String videoPath)
    {
        currentVideoPath = videoPath;
        videoView.setVideoPath(videoPath);

        videoView.setOnPreparedListener(mp ->
        {
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            if (savedPosition > 0)
            {
                final int seekTarget = savedPosition;
                savedPosition = 0;
                Log.d(TAG, "Video re-prepared — seeking to " + seekTarget + "ms (SEEK_CLOSEST)");
                listener.onVideoPrepared(mp);
                videoView.pause(); // sets mTargetState=STATE_PAUSED → blocks VideoView auto-start
                mp.setOnSeekCompleteListener(m ->
                {
                    m.setOnSeekCompleteListener(null);
                    videoView.start();
                });
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    mp.seekTo(seekTarget, MediaPlayer.SEEK_CLOSEST);
                else
                    mp.seekTo(seekTarget);
            }
            else
            {
                Log.d(TAG, "Video prepared — starting from beginning");
                listener.onVideoPrepared(mp);
                videoView.start();
            }
        });

        videoView.setOnCompletionListener(mp ->
        {
            Log.d(TAG, "Video completed");
            listener.onVideoCompleted();
            videoView.seekTo(0);
            videoView.start();
        });

        videoView.setOnErrorListener((mp, what, extra) ->
        {
            File videoFile = new File(currentVideoPath);
            String fileDiagnostic = videoFile.exists()
                    ? "file present (" + videoFile.length() + " bytes)"
                    : "file missing — deleted externally while player was paused";
            Log.e(TAG, "Video playback failed — path: " + currentVideoPath
                    + " | " + describeError(what, extra)
                    + " | " + fileDiagnostic);
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
            lastPausedPosition = savedPosition;
            videoView.pause();
            Log.d(TAG, "Video paused at position: " + savedPosition);
        }
    }

    /**
     * Saves position and calls stopPlayback() to fully release the MediaPlayer,
     * reducing RAM footprint while the activity is backgrounded.
     * Call resume() to reload and resume from the saved position.
     */
    public void suspend()
    {
        if (videoView != null)
        {
            if (videoView.isPlaying())
            {
                savedPosition = videoView.getCurrentPosition();
                lastPausedPosition = savedPosition;
            }
            Log.d(TAG, "suspend — stopPlayback at position=" + savedPosition);
            videoView.stopPlayback();
            isSuspended = true;
        }
    }

    public void resume()
    {
        if (videoView == null) return;
        if (isSuspended)
        {
            // MediaPlayer was fully released — reload from saved position
            isSuspended = false;
            Log.d(TAG, "resume: was suspended — reloading from position=" + savedPosition);
            load(currentVideoPath);
            return;
        }
        int pos = videoView.getCurrentPosition();
        Log.d(TAG, "Video resume (savedPosition=" + savedPosition + " currentPosition=" + pos + ")");
        if (pos > 0)
        {
            // Seek to current position before starting — forces the SurfaceView to decode
            // and display the current frame immediately, preventing the black flash that
            // occurs when the surface is recreated after returning from the Play Store.
            videoView.seekTo(pos);
        }
        videoView.start();
    }

    /** Restores video position from a saved bundle (process death recovery). */
    public void setSavedPosition(int position)
    {
        this.savedPosition = position;
        Log.d(TAG, "setSavedPosition: " + position);
    }

    public void stop()
    {
        if (videoView != null)
        {
            videoView.stopPlayback();
        }
    }

    public int getLastPausedPosition()
    {
        return lastPausedPosition;
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
