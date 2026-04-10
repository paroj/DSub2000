/*
	This file is part of Subsonic.

	Subsonic is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	Subsonic is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

	Copyright 2024 (C) DSub2000 Contributors
*/
package github.paroj.dsub2000.provider;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaRouter;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouteDescriptor;
import androidx.mediarouter.media.MediaRouteProvider;
import androidx.mediarouter.media.MediaRouteProviderDescriptor;

import github.paroj.dsub2000.domain.RemoteControlState;
import github.paroj.dsub2000.service.DownloadService;
import github.paroj.dsub2000.service.RemoteController;

/**
 * MediaRoute provider for Remote Client control mode
 */
public class RemoteClientRouteProvider extends MediaRouteProvider {
	public static final String CATEGORY_REMOTE_CLIENT_ROUTE = "github.paroj.dsub2000.REMOTE_CLIENT";
	private RemoteController controller;
	private static final int MAX_VOLUME = 10;

	private DownloadService downloadService;

	public RemoteClientRouteProvider(Context context) {
		super(context);
		this.downloadService = (DownloadService) context;

		broadcastDescriptor();
	}

	private void broadcastDescriptor() {
		// Create intents
		IntentFilter routeIntentFilter = new IntentFilter();
		routeIntentFilter.addCategory(CATEGORY_REMOTE_CLIENT_ROUTE);
		routeIntentFilter.addAction(MediaControlIntent.ACTION_START_SESSION);
		routeIntentFilter.addAction(MediaControlIntent.ACTION_GET_SESSION_STATUS);
		routeIntentFilter.addAction(MediaControlIntent.ACTION_END_SESSION);

		// Create route descriptor
		MediaRouteDescriptor.Builder routeBuilder = new MediaRouteDescriptor.Builder("Remote Client Route", "Remote DSub2000");
		routeBuilder.addControlFilter(routeIntentFilter)
				.setPlaybackStream(AudioManager.STREAM_MUSIC)
				.setPlaybackType(MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE)
				.setDescription("Remote DSub2000 Instance")
				.setVolume(controller == null ? 5 : (int) (controller.getVolume() * 10))
				.setVolumeMax(MAX_VOLUME)
				.setVolumeHandling(MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE);

		// Create descriptor
		MediaRouteProviderDescriptor.Builder providerBuilder = new MediaRouteProviderDescriptor.Builder();
		providerBuilder.addRoute(routeBuilder.build());
		setDescriptor(providerBuilder.build());
	}

	@Override
	public MediaRouteProvider.RouteController onCreateRouteController(String routeId) {
		return new RemoteClientRouteController(downloadService);
	}

	private class RemoteClientRouteController extends RouteController {
		private DownloadService downloadService;

		public RemoteClientRouteController(DownloadService downloadService) {
			this.downloadService = downloadService;
		}

		@Override
		public boolean onControlRequest(Intent intent, androidx.mediarouter.media.MediaRouter.ControlRequestCallback callback) {
			if (intent.hasCategory(CATEGORY_REMOTE_CLIENT_ROUTE)) {
				return true;
			} else {
				return false;
			}
		}

		@Override
		public void onRelease() {
			downloadService.setRemoteEnabled(RemoteControlState.LOCAL);
			controller = null;
		}

		@Override
		public void onSelect() {
			android.util.Log.d("RemoteClientRoute", ">>> Route selected - switching to Remote Client mode");
			downloadService.setRemoteEnabled(RemoteControlState.REMOTE_CLIENT);
			controller = downloadService.getRemoteController();
			android.util.Log.d("RemoteClientRoute", ">>> Remote controller set: " + (controller != null ? "SUCCESS" : "FAILED"));
		}

		@Override
		public void onUnselect() {
			downloadService.setRemoteEnabled(RemoteControlState.LOCAL);
			controller = null;
		}

		@Override
		public void onUpdateVolume(int delta) {
			if(controller != null) {
				controller.updateVolume(delta > 0);
			}
			broadcastDescriptor();
		}

		@Override
		public void onSetVolume(int volume) {
			if(controller != null) {
				controller.setVolume(volume);
			}
			broadcastDescriptor();
		}
	}
}
