/*
	This file is part of Subsonic.
	
	Subsonic is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	
	Subsonic is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.
	
	You should have received a copy of the GNU General Public License
	along with Subsonic. If not, see <http://www.gnu.org/licenses/>.
	
	Copyright 2024 (C) DSub2000 Contributors
*/

package github.paroj.dsub2000.service;

import android.os.Handler;
import android.util.Log;

import github.paroj.dsub2000.domain.MusicDirectory;
import github.paroj.dsub2000.domain.RemoteStatus;
import github.paroj.dsub2000.domain.PlayerState;
import github.paroj.dsub2000.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controller for remote client mode - connects to another DSub2000 instance
 * running LocalControlServer and sends playback commands to it.
 */
public class RemoteClientController extends RemoteController {
	private static final String TAG = RemoteClientController.class.getSimpleName();
	private static final long STATUS_UPDATE_INTERVAL_SECONDS = 2L;
	
	private final Handler handler;
	private boolean running = false;
	private final TaskQueue tasks = new TaskQueue();
	private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> statusUpdateFuture;
	private final AtomicLong timeOfLastUpdate = new AtomicLong();
	private RemoteStatus remoteStatus;
	private float gain = 0.5f;
	private final String remoteHost;
	private final int remotePort;
	private MusicService remoteMusicService;
	
	public RemoteClientController(DownloadService downloadService, Handler handler, String host, int port) {
		super(downloadService);
		this.handler = handler;
		this.remoteHost = host;
		this.remotePort = port;

		Log.d(TAG, ">>> Creating RemoteClientController for " + host + ":" + port);

		// Create a custom MusicService that points to the remote DSub2000 instance
		this.remoteMusicService = createRemoteMusicService(host, port);
		Log.d(TAG, ">>> DirectHttpMusicService created successfully");
	}

	@Override
	public void create(boolean playing, int seconds) {
		new Thread("RemoteClientController") {
			@Override
			public void run() {
				Log.d(TAG, ">>> Task thread started for " + remoteHost + ":" + remotePort);
				running = true;
				processTasks();
			}
		}.start();
		updatePlaylist();
		if(seconds != 0 && playing) {
			changePosition(seconds);
		}
	}

	@Override
	public void start() {
		tasks.remove(Stop.class);
		tasks.remove(Start.class);
		
		startStatusUpdate();
		tasks.add(new Start());
	}

	@Override
	public void stop() {
		tasks.remove(Stop.class);
		tasks.remove(Start.class);
		
		stopStatusUpdate();
		tasks.add(new Stop());
	}

	@Override
	public void shutdown() {
		running = false;
		stopStatusUpdate();
		executorService.shutdown();
	}
	
	@Override
	public void updatePlaylist() {
		tasks.remove(Skip.class);
		tasks.remove(Stop.class);
		tasks.remove(Start.class);

		List<MusicDirectory.Entry> songs = new ArrayList<>();
		for (DownloadFile file : downloadService.getDownloads()) {
			songs.add(file.getSong());
		}
		Log.d(TAG, ">>> updatePlaylist called with " + songs.size() + " songs");
		tasks.add(new SetPlaylist(songs));
	}

	@Override
	public void changePosition(int seconds) {
		tasks.remove(Skip.class);
		tasks.remove(Stop.class);
		tasks.remove(Start.class);
		
		startStatusUpdate();
		if (remoteStatus != null) {
			remoteStatus.setPositionSeconds(seconds);
		}
		tasks.add(new Skip(downloadService.getCurrentPlayingIndex(), seconds));
		downloadService.setPlayerState(PlayerState.STARTED);
	}

	@Override
	public void changeTrack(int index, DownloadFile song) {
		tasks.remove(Skip.class);
		tasks.remove(Stop.class);
		tasks.remove(Start.class);
		
		startStatusUpdate();
		tasks.add(new Skip(index, 0));
		downloadService.setPlayerState(PlayerState.STARTED);
	}

	@Override
	public void setVolume(int volume) {
		gain = volume / 10.0f;

		tasks.remove(SetGain.class);
		tasks.add(new SetGain(gain));
	}

	@Override
	public void updateVolume(boolean up) {
		float delta = up ? 0.1f : -0.1f;
		gain += delta;
		gain = Math.max(gain, 0.0f);
		gain = Math.min(gain, 1.0f);

		tasks.remove(SetGain.class);
		tasks.add(new SetGain(gain));
	}

	@Override
	public double getVolume() {
		return gain;
	}
	
	@Override
	public int getRemotePosition() {
		if (remoteStatus == null || remoteStatus.getPositionSeconds() == null || timeOfLastUpdate.get() == 0) {
			return 0;
		}
		
		if (remoteStatus.isPlaying()) {
			int secondsSinceLastUpdate = (int) ((System.currentTimeMillis() - timeOfLastUpdate.get()) / 1000L);
			return remoteStatus.getPositionSeconds() + secondsSinceLastUpdate;
		}
		
		return remoteStatus.getPositionSeconds();
	}
	
	private void processTasks() {
		while (running) {
			RemoteTask task = null;
			try {
				task = tasks.take();
				RemoteStatus status = task.execute();
				if(status != null && running) {
					onStatusUpdate(status);
				}
			} catch (Throwable x) {
				onError(task, x);
			}
		}
	}
	
	private synchronized void startStatusUpdate() {
		stopStatusUpdate();
		Runnable updateTask = new Runnable() {
			@Override
			public void run() {
				tasks.remove(GetStatus.class);
				tasks.add(new GetStatus());
			}
		};
		statusUpdateFuture = executorService.scheduleWithFixedDelay(updateTask, STATUS_UPDATE_INTERVAL_SECONDS,
			STATUS_UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);
	}
	
	private synchronized void stopStatusUpdate() {
		if (statusUpdateFuture != null) {
			statusUpdateFuture.cancel(false);
			statusUpdateFuture = null;
		}
	}
	
	private void onStatusUpdate(RemoteStatus remoteStatus) {
		timeOfLastUpdate.set(System.currentTimeMillis());
		this.remoteStatus = remoteStatus;
		
		// Track change?
		Integer index = remoteStatus.getCurrentPlayingIndex();
		if (index != null && index != -1 && index != downloadService.getCurrentPlayingIndex()) {
			downloadService.setPlayerState(PlayerState.COMPLETED);
			downloadService.setCurrentPlaying(index, true);
			if(remoteStatus.isPlaying()) {
				downloadService.setPlayerState(PlayerState.STARTED);
			}
		}
	}

	private void onError(RemoteTask task, Throwable x) {
		Log.e(TAG, ">>> Failed to process remote client task " +
				(task != null ? task.getClass().getSimpleName() : "null") + ": " + x, x);
	}
	
	private MusicService createRemoteMusicService(String host, int port) {
		// Create a DirectHttpMusicService that sends requests directly to
		// the remote DSub2000 instance at http://host:port
		return new DirectHttpMusicService(host, port);
	}
	
	private MusicService getRemoteMusicService() {
		return remoteMusicService;
	}

	private class GetStatus extends RemoteTask {
		@Override
		RemoteStatus execute() throws Exception {
			return getRemoteMusicService().getJukeboxStatus(downloadService, null);
		}
	}

	private class SetPlaylist extends RemoteTask {
		private final List<MusicDirectory.Entry> songs;

		SetPlaylist(List<MusicDirectory.Entry> songs) {
			this.songs = songs;
		}

		@Override
		RemoteStatus execute() throws Exception {
			MusicService svc = getRemoteMusicService();
			if (svc instanceof DirectHttpMusicService) {
				// Send full metadata so D2 can display title/artist correctly.
				return ((DirectHttpMusicService) svc).setPlaylistWithMetadata(songs, downloadService, null);
			}
			// Generic fallback: IDs only.
			List<String> ids = new ArrayList<>();
			for (MusicDirectory.Entry song : songs) ids.add(song.getId());
			return svc.updateJukeboxPlaylist(ids, downloadService, null);
		}
	}

	private class Skip extends RemoteTask {
		private final int index;
		private final int offsetSeconds;
		
		Skip(int index, int offsetSeconds) {
			this.index = index;
			this.offsetSeconds = offsetSeconds;
		}
		
		@Override
		RemoteStatus execute() throws Exception {
			return getRemoteMusicService().skipJukebox(index, offsetSeconds, downloadService, null);
		}
	}

	private class Stop extends RemoteTask {
		@Override
		RemoteStatus execute() throws Exception {
			return getRemoteMusicService().stopJukebox(downloadService, null);
		}
	}

	private class Start extends RemoteTask {
		@Override
		RemoteStatus execute() throws Exception {
			return getRemoteMusicService().startJukebox(downloadService, null);
		}
	}

	private class SetGain extends RemoteTask {
		private final float gain;
		
		private SetGain(float gain) {
			this.gain = gain;
		}
		
		@Override
		RemoteStatus execute() throws Exception {
			return getRemoteMusicService().setJukeboxGain(gain, downloadService, null);
		}
	}
}
