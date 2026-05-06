/*
 This file is part of DSub2000.
 Released under GPLv3, see project LICENSE.txt.
*/
package github.paroj.dsub2000.service;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.PlaybackParams;

import androidx.annotation.RequiresApi;

import java.io.IOException;

/**
 * {@link AudioPlayer} backed by the legacy {@link android.media.MediaPlayer}. Used
 * for local files (cached or partial downloads) where {@code MediaPlayer} works
 * reliably and integrates with the existing replay-gain / equalizer / gapless
 * paths in {@link DownloadService}.
 */
public class MediaPlayerAudio implements AudioPlayer {

    private final MediaPlayer mp = new MediaPlayer();

    /**
     * Exposes the underlying {@link MediaPlayer} so call sites that need to
     * interact with the framework directly (e.g. equalizer attached via
     * audio session, hardware effects) can still do so. Returns null on the
     * stream-oriented adapter.
     */
    public MediaPlayer getMediaPlayer() {
        return mp;
    }

    @Override
    public void setDataSource(String url) throws IOException {
        mp.setDataSource(url);
    }

    @Override public void prepareAsync() { mp.prepareAsync(); }
    @Override public void start() { mp.start(); }
    @Override public void pause() { mp.pause(); }
    @Override public void stop() { mp.stop(); }
    @Override public void reset() { mp.reset(); }
    @Override public void release() { mp.release(); }
    @Override public void seekTo(int msec) { mp.seekTo(msec); }
    @Override public int getCurrentPosition() { return mp.getCurrentPosition(); }
    @Override public int getDuration() { return mp.getDuration(); }
    @Override public boolean isPlaying() { return mp.isPlaying(); }
    @Override public int getAudioSessionId() { return mp.getAudioSessionId(); }
    @Override public void setAudioSessionId(int id) { mp.setAudioSessionId(id); }
    @Override public void setAudioStreamType(int streamType) { mp.setAudioStreamType(streamType); }
    @Override public void setVolume(float l, float r) { mp.setVolume(l, r); }
    @Override public void setWakeMode(Context c, int mode) { mp.setWakeMode(c, mode); }

    @Override
    public void setOnPreparedListener(final OnPreparedListener listener) {
        mp.setOnPreparedListener(listener == null ? null : new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer ignored) { listener.onPrepared(MediaPlayerAudio.this); }
        });
    }

    @Override
    public void setOnCompletionListener(final OnCompletionListener listener) {
        mp.setOnCompletionListener(listener == null ? null : new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer ignored) { listener.onCompletion(MediaPlayerAudio.this); }
        });
    }

    @Override
    public void setOnErrorListener(final OnErrorListener listener) {
        mp.setOnErrorListener(listener == null ? null : new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer ignored, int what, int extra) {
                return listener.onError(MediaPlayerAudio.this, what, extra);
            }
        });
    }

    @Override
    public void setOnBufferingUpdateListener(final OnBufferingUpdateListener listener) {
        mp.setOnBufferingUpdateListener(listener == null ? null : new MediaPlayer.OnBufferingUpdateListener() {
            @Override public void onBufferingUpdate(MediaPlayer ignored, int percent) {
                listener.onBufferingUpdate(MediaPlayerAudio.this, percent);
            }
        });
    }

    @Override @RequiresApi(23)
    public PlaybackParams getPlaybackParams() { return mp.getPlaybackParams(); }

    @Override @RequiresApi(23)
    public void setPlaybackParams(PlaybackParams params) { mp.setPlaybackParams(params); }

    @Override
    public void setNextMediaPlayer(AudioPlayer next) {
        if (next == null) {
            mp.setNextMediaPlayer(null);
        } else if (next instanceof MediaPlayerAudio) {
            mp.setNextMediaPlayer(((MediaPlayerAudio) next).mp);
        }
        // Cross-backend gapless (file → stream or vice versa) is not meaningful;
        // ignore in that case.
    }
}
