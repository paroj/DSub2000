/*
 This file is part of DSub2000.
 Released under GPLv3, see project LICENSE.txt.
*/
package github.paroj.dsub2000.service;

import android.content.Context;
import android.media.AudioManager;
import android.media.PlaybackParams;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.OptIn;
import androidx.annotation.RequiresApi;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/**
 * {@link AudioPlayer} backed by Media3 {@link ExoPlayer}. Used for HTTP streams
 * (Internet radios) where {@code android.media.MediaPlayer} is unreliable for
 * Ogg Vorbis / Opus over HTTP. Translates the legacy MediaPlayer-shaped lifecycle
 * (prepareAsync → onPrepared → start; onCompletion at end) onto ExoPlayer's
 * state-machine ({@link Player#STATE_BUFFERING}, {@link Player#STATE_READY},
 * {@link Player#STATE_ENDED}).
 */
@OptIn(markerClass = UnstableApi.class)
public class ExoPlayerAudio implements AudioPlayer {

    private final ExoPlayer player;
    private final Handler mainHandler;

    private OnPreparedListener preparedListener;
    private OnCompletionListener completionListener;
    private OnErrorListener errorListener;
    private OnBufferingUpdateListener bufferingListener;

    /** Has onPrepared already been delivered for the currently-loaded media? */
    private boolean preparedDelivered = false;

    public ExoPlayerAudio(Context context) {
        mainHandler = new Handler(Looper.getMainLooper());
        // ExoPlayer must be created and accessed on a Looper thread. DownloadService
        // creates the player on threads without an attached Looper, so we pin to
        // the application's main Looper.
        //
        // Use NoRangeHttpDataSource so the player never sends a Range header. Some
        // Icecast/Liquidsoap radio servers respond 404 to Range requests (the
        // default DefaultHttpDataSource always sends Range: bytes=0- to discover
        // the resource length). For live audio streams Range/length are
        // unnecessary anyway.
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(context)
                .setDataSourceFactory(new NoRangeHttpDataSource.Factory());
        player = new ExoPlayer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .setMediaSourceFactory(mediaSourceFactory)
                .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                switch (playbackState) {
                    case Player.STATE_READY:
                        if (!preparedDelivered) {
                            preparedDelivered = true;
                            if (preparedListener != null) preparedListener.onPrepared(ExoPlayerAudio.this);
                        }
                        if (bufferingListener != null) {
                            bufferingListener.onBufferingUpdate(ExoPlayerAudio.this, 100);
                        }
                        break;
                    case Player.STATE_BUFFERING:
                        if (bufferingListener != null) {
                            bufferingListener.onBufferingUpdate(ExoPlayerAudio.this, 0);
                        }
                        break;
                    case Player.STATE_ENDED:
                        if (completionListener != null) completionListener.onCompletion(ExoPlayerAudio.this);
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (errorListener != null) {
                    errorListener.onError(ExoPlayerAudio.this, error.errorCode, 0);
                }
            }
        });
    }

    /** Run a block on the player's Looper. ExoPlayer's API is single-threaded. */
    private void onPlayerThread(Runnable r) {
        if (Looper.myLooper() == player.getApplicationLooper()) {
            r.run();
        } else {
            mainHandler.post(r);
        }
    }

    @Override
    public void setDataSource(String url) {
        onPlayerThread(() -> {
            preparedDelivered = false;
            player.setMediaItem(MediaItem.fromUri(url));
        });
    }

    @Override
    public void prepareAsync() {
        // Pause-by-default mirrors MediaPlayer's prepareAsync → wait-for-onPrepared
        // → caller decides whether to start(). DownloadService toggles play/pause
        // explicitly in its onPrepared handler.
        onPlayerThread(() -> {
            player.setPlayWhenReady(false);
            player.prepare();
        });
    }

    @Override
    public void start() {
        onPlayerThread(() -> {
            player.setPlayWhenReady(true);
            // Player#play exists from 1.0.x; equivalent to setPlayWhenReady(true).
        });
    }

    @Override
    public void pause() {
        onPlayerThread(player::pause);
    }

    @Override
    public void stop() {
        onPlayerThread(player::stop);
    }

    @Override
    public void reset() {
        onPlayerThread(() -> {
            player.stop();
            player.clearMediaItems();
            preparedDelivered = false;
        });
    }

    @Override
    public void release() {
        onPlayerThread(player::release);
    }

    @Override
    public void seekTo(int msec) {
        onPlayerThread(() -> player.seekTo(msec));
    }

    @Override
    public int getCurrentPosition() {
        return (int) player.getCurrentPosition();
    }

    @Override
    public int getDuration() {
        long d = player.getDuration();
        // For live streams ExoPlayer reports TIME_UNSET; mimic MediaPlayer (returns 0).
        return d == C.TIME_UNSET ? 0 : (int) d;
    }

    @Override
    public boolean isPlaying() {
        return player.isPlaying();
    }

    @Override
    public int getAudioSessionId() {
        return player.getAudioSessionId();
    }

    @Override
    public void setAudioSessionId(int sessionId) {
        onPlayerThread(() -> player.setAudioSessionId(sessionId));
    }

    @Override
    public void setAudioStreamType(int streamType) {
        // Translate legacy AudioManager.STREAM_* to Media3 AudioAttributes.
        final int contentType;
        final int usage;
        switch (streamType) {
            case AudioManager.STREAM_MUSIC:
            default:
                contentType = C.AUDIO_CONTENT_TYPE_MUSIC;
                usage = C.USAGE_MEDIA;
                break;
        }
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setContentType(contentType)
                .setUsage(usage)
                .build();
        onPlayerThread(() -> player.setAudioAttributes(attrs, /* handleAudioFocus= */ false));
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        // ExoPlayer is mono volume; average the two channels.
        final float v = Math.max(0f, Math.min(1f, (leftVolume + rightVolume) / 2f));
        onPlayerThread(() -> player.setVolume(v));
    }

    @Override
    public void setWakeMode(Context context, int mode) {
        onPlayerThread(() -> player.setWakeMode(mode));
    }

    @Override
    public void setOnPreparedListener(OnPreparedListener listener) {
        this.preparedListener = listener;
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener) {
        this.completionListener = listener;
    }

    @Override
    public void setOnErrorListener(OnErrorListener listener) {
        this.errorListener = listener;
    }

    @Override
    public void setOnBufferingUpdateListener(OnBufferingUpdateListener listener) {
        this.bufferingListener = listener;
    }

    @Override @RequiresApi(23)
    public PlaybackParams getPlaybackParams() {
        PlaybackParameters pp = player.getPlaybackParameters();
        return new PlaybackParams().setSpeed(pp.speed).setPitch(pp.pitch);
    }

    @Override @RequiresApi(23)
    public void setPlaybackParams(PlaybackParams params) {
        final PlaybackParameters pp = new PlaybackParameters(params.getSpeed(), params.getPitch());
        onPlayerThread(() -> player.setPlaybackParameters(pp));
    }

    @Override
    public void setNextMediaPlayer(AudioPlayer next) {
        // Live HTTP streams have no end of file; gapless does not apply. No-op.
    }
}
