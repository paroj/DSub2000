/*
 This file is part of DSub2000.
 Released under GPLv3, see project LICENSE.txt.
*/
package github.paroj.dsub2000.service;

import android.content.Context;
import android.media.PlaybackParams;

import androidx.annotation.RequiresApi;

/**
 * Player abstraction used by {@link DownloadService}. Allows different backends
 * (legacy {@link android.media.MediaPlayer} for local files, ExoPlayer for HTTP
 * streams) to share the same call sites in DownloadService.
 *
 * The method names mirror the subset of {@code MediaPlayer} that DownloadService
 * relies on, so callers don't have to change semantics. Listeners are reproduced
 * here (instead of using {@code MediaPlayer.OnPreparedListener} etc.) so the
 * ExoPlayer-based implementation can satisfy them without depending on
 * {@code MediaPlayer}.
 */
public interface AudioPlayer {

    void setDataSource(String url) throws Exception;

    void prepareAsync();

    void start();

    void pause();

    void stop();

    void reset();

    void release();

    void seekTo(int msec);

    int getCurrentPosition();

    int getDuration();

    boolean isPlaying();

    int getAudioSessionId();

    void setAudioSessionId(int sessionId);

    void setAudioStreamType(int streamType);

    void setVolume(float leftVolume, float rightVolume);

    void setWakeMode(Context context, int mode);

    void setOnPreparedListener(OnPreparedListener listener);

    void setOnCompletionListener(OnCompletionListener listener);

    void setOnErrorListener(OnErrorListener listener);

    void setOnBufferingUpdateListener(OnBufferingUpdateListener listener);

    @RequiresApi(23)
    PlaybackParams getPlaybackParams();

    @RequiresApi(23)
    void setPlaybackParams(PlaybackParams params);

    /**
     * Mirrors {@link android.media.MediaPlayer#setNextMediaPlayer(android.media.MediaPlayer)}
     * for gapless playback. Implementations that don't support gapless (e.g. the
     * stream-oriented backend) may treat this as a no-op.
     */
    void setNextMediaPlayer(AudioPlayer next);

    interface OnPreparedListener {
        void onPrepared(AudioPlayer player);
    }

    interface OnCompletionListener {
        void onCompletion(AudioPlayer player);
    }

    interface OnErrorListener {
        boolean onError(AudioPlayer player, int what, int extra);
    }

    interface OnBufferingUpdateListener {
        void onBufferingUpdate(AudioPlayer player, int percent);
    }
}
