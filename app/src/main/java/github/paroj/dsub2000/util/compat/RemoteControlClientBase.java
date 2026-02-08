package github.paroj.dsub2000.util.compat;

import github.paroj.dsub2000.domain.MusicDirectory;
import github.paroj.dsub2000.service.DownloadFile;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import androidx.mediarouter.media.MediaRouter;
import android.os.Build;

import java.util.List;

public abstract class RemoteControlClientBase {
	
	public static RemoteControlClientBase createInstance() {
		return new RemoteControlClientLP();
	}
	
	protected RemoteControlClientBase() {
		// Avoid instantiation
	}
	
	public abstract void register(final Context context, final ComponentName mediaButtonReceiverComponent);
	public abstract void unregister(final Context context);
	public abstract void setPlaybackState(int state, int index, int queueSize);
	public abstract void updateMetadata(Context context, MusicDirectory.Entry currentSong);
	public abstract void metadataChanged(MusicDirectory.Entry currentSong);
	public abstract void updateAlbumArt(MusicDirectory.Entry currentSong, Bitmap bitmap);
	public abstract void registerRoute(MediaRouter router);
	public abstract void unregisterRoute(MediaRouter router);
	public abstract void updatePlaylist(List<DownloadFile> playlist);
}
