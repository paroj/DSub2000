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

import android.content.Context;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import github.paroj.dsub2000.domain.PlayerState;
import github.paroj.dsub2000.domain.RemoteStatus;
import github.paroj.serverproxy.ServerProxy;

/**
 * Local HTTP server that accepts Subsonic API commands and forwards them to DownloadService.
 * This allows another DSub2000 instance to control this device's playback.
 */
public class LocalControlServer extends ServerProxy {
	private static final String TAG = LocalControlServer.class.getSimpleName();
	
	private static final int DEFAULT_PORT = 4040;
	private final DownloadService downloadService;

	public LocalControlServer(DownloadService downloadService) {
		this(downloadService, DEFAULT_PORT);
	}

	public LocalControlServer(DownloadService downloadService, int port) {
		super(downloadService, port);
		this.downloadService = downloadService;
	}
	
	protected ProxyTask getTask(Socket client) {
		return new ControlTask(client);
	}
	
	private class ControlTask extends ProxyTask {
		
		public ControlTask(Socket client) {
			super(client);
		}
		
		@Override
		public void run() {
			try {
				if (path == null) {
					Log.e(TAG, "Request path is null");
					sendErrorResponse(400, "Bad Request");
					return;
				}
				Log.i(TAG, "Processing request path: " + path);
				String response = handleRequest(path, requestHeaders);
				sendResponse(response);
			} catch (Exception e) {
				Log.e(TAG, "Error handling control request: " + e.getMessage(), e);
				try {
					sendErrorResponse(500, "Internal Server Error: " + e.getMessage());
				} catch (IOException ioe) {
					Log.e(TAG, "Error sending error response", ioe);
				}
			}
		}
		
		private String handleRequest(String path, java.util.Map<String, String> headers) throws Exception {
			Log.i(TAG, "Handling request: " + path);
			
			// Parse the path to extract endpoint and parameters
			String endpoint = "";
			String query = "";
			int queryIndex = path.indexOf('?');
			if (queryIndex != -1) {
				endpoint = path.substring(0, queryIndex);
				query = path.substring(queryIndex + 1);
			} else {
				endpoint = path;
			}
			
			// Parse query parameters
			java.util.Map<String, String> params = parseQueryParams(query);
			
			// Handle different endpoints (path doesn't have leading slash)
			if (endpoint.contains("rest/ping")) {
				return buildPingResponse();
			} else if (endpoint.contains("rest/jukeboxControl")) {
				return handleJukeboxControl(params);
			} else {
				throw new Exception("Unknown endpoint: " + endpoint);
			}
		}
		
		private String handleJukeboxControl(java.util.Map<String, String> params) throws Exception {
			String action = params.get("action");
			Log.d(TAG, ">>> jukeboxControl action=" + action + " params=" + params);
			if (action == null) {
				throw new Exception("Missing action parameter");
			}
			
			RemoteStatus status = new RemoteStatus();
			
			switch (action) {
				case "start":
				case "play":
					downloadService.start();
					break;
					
				case "stop":
				case "pause":
					downloadService.pause();
					break;
					
				case "skip":
					int index = Integer.parseInt(params.getOrDefault("index", "0"));
					int offset = Integer.parseInt(params.getOrDefault("offset", "0"));
					// playAt() passes positionMs into bufferAndPlay → doPlay → mediaPlayer.seekTo(),
					// which is called after preparation. This avoids the race condition of calling
					// seekTo() separately before the MediaPlayer is prepared.
					downloadService.playAt(index, offset * 1000);
					break;
					
				case "setGain":
					float gain = Float.parseFloat(params.getOrDefault("gain", "0.5"));
					downloadService.setVolume(gain);
					break;
					
				case "set":
					List<github.paroj.dsub2000.domain.MusicDirectory.Entry> songs = new ArrayList<>();

					String countStr = params.get("count");
					if (countStr != null) {
						// New protocol: indexed metadata sent by DirectHttpMusicService.setPlaylistWithMetadata().
						// Parameters: count, id0, title0, artist0, album0, duration0, suffix0, track0, id1, …
						int count = Integer.parseInt(countStr);
						for (int i = 0; i < count; i++) {
							String id = params.get("id" + i);
							if (id == null || id.isEmpty()) continue;

							github.paroj.dsub2000.domain.MusicDirectory.Entry entry =
								new github.paroj.dsub2000.domain.MusicDirectory.Entry(id);
							entry.setId(id);

							String title = params.get("title" + i);
							entry.setTitle(title != null ? title : id);

							String artist = params.get("artist" + i);
							if (artist != null) entry.setArtist(artist);

							String album = params.get("album" + i);
							if (album != null) entry.setAlbum(album);

							String durStr = params.get("duration" + i);
							if (durStr != null) entry.setDuration(Integer.parseInt(durStr));

							String suffix = params.get("suffix" + i);
							entry.setSuffix(suffix != null ? suffix : "mp3");

							String trackStr = params.get("track" + i);
							entry.setTrack(trackStr != null ? Integer.parseInt(trackStr) : 0);

							songs.add(entry);
						}
					} else {
						// Legacy / curl fallback: ?action=set&id=id1,id2,id3
						String idParam = params.get("id");
						if (idParam != null) {
							for (String id : idParam.split(",")) {
								if (id.isEmpty()) continue;
								github.paroj.dsub2000.domain.MusicDirectory.Entry entry =
									new github.paroj.dsub2000.domain.MusicDirectory.Entry(id);
								entry.setId(id);
								entry.setTitle(id);
								entry.setTrack(0);
								entry.setSuffix("mp3");
								songs.add(entry);
							}
						}
					}

					if (!songs.isEmpty()) {
						Log.d(TAG, ">>> Setting playlist with " + songs.size() + " items, first title: " + songs.get(0).getTitle());
						downloadService.clear();
						downloadService.download(songs, false, true, false, false);
					}
					break;
					
				case "get":
				case "status":
				default:
					// Just return current status
					break;
			}
			
			// Build status response
			status = buildCurrentStatus();
			return buildJukeboxResponse(status);
		}
		
		private RemoteStatus buildCurrentStatus() {
			RemoteStatus status = new RemoteStatus();
			
			// Set current playing index
			status.setCurrentIndex(downloadService.getCurrentPlayingIndex());
			
			// Set playing state
			PlayerState playerState = downloadService.getPlayerState();
			status.setPlaying(playerState == PlayerState.STARTED);
			
			// Set position in seconds
			int positionMs = downloadService.getPlayerPosition();
			status.setPositionSeconds(positionMs / 1000);
			
			// Set gain/volume (DownloadService doesn't expose getVolume(), using default)
			// TODO: Add getVolume() method to DownloadService or track volume in this class
			status.setGain(0.5f);
			
			return status;
		}
		
		private String buildPingResponse() {
			return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<subsonic-response status=\"ok\" version=\"1.16.1\" " +
				"xmlns=\"http://subsonic.org/restapi\">\n" +
				"</subsonic-response>";
		}
		
		private String buildJukeboxResponse(RemoteStatus status) {
			StringBuilder sb = new StringBuilder();
			sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
			sb.append("<subsonic-response status=\"ok\" version=\"1.16.1\" ");
			sb.append("xmlns=\"http://subsonic.org/restapi\">\n");
			sb.append("  <jukeboxStatus ");
			sb.append("currentIndex=\"").append(status.getCurrentPlayingIndex()).append("\" ");
			sb.append("playing=\"").append(status.isPlaying()).append("\" ");
			sb.append("gain=\"").append(status.getGain()).append("\" ");
			sb.append("position=\"").append(status.getPositionSeconds()).append("\"");
			sb.append("/>\n");
			sb.append("</subsonic-response>");
			return sb.toString();
		}
		
		private void sendResponse(String response) throws IOException {
			OutputStream output = new BufferedOutputStream(client.getOutputStream());
			
			String headers = "HTTP/1.0 200 OK\r\n";
			headers += "Content-Type: text/xml; charset=UTF-8\r\n";
			headers += "Content-Length: " + response.getBytes("UTF-8").length + "\r\n";
			headers += "Connection: close\r\n";
			headers += "\r\n";
			
			output.write(headers.getBytes("UTF-8"));
			output.write(response.getBytes("UTF-8"));
			output.flush();
			output.close();
			client.close();
		}
		
		private void sendErrorResponse(int code, String message) throws IOException {
			String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<subsonic-response status=\"failed\" version=\"1.16.1\" " +
				"xmlns=\"http://subsonic.org/restapi\">\n" +
				"  <error code=\"" + code + "\" message=\"" + message + "\"/>\n" +
				"</subsonic-response>";
			
			OutputStream output = new BufferedOutputStream(client.getOutputStream());
			
			String headers = "HTTP/1.0 " + code + " " + message + "\r\n";
			headers += "Content-Type: text/xml; charset=UTF-8\r\n";
			headers += "Content-Length: " + response.getBytes("UTF-8").length + "\r\n";
			headers += "Connection: close\r\n";
			headers += "\r\n";
			
			output.write(headers.getBytes("UTF-8"));
			output.write(response.getBytes("UTF-8"));
			output.flush();
			output.close();
			client.close();
		}
		
		private java.util.Map<String, String> parseQueryParams(String query) {
			java.util.Map<String, String> params = new java.util.HashMap<>();
			if (query == null || query.isEmpty()) {
				return params;
			}
			
			for (String param : query.split("&")) {
				int eqIndex = param.indexOf('=');
				if (eqIndex != -1) {
					String key = param.substring(0, eqIndex);
					String value = param.substring(eqIndex + 1);
					try {
						value = java.net.URLDecoder.decode(value, "UTF-8");
					} catch (java.io.UnsupportedEncodingException e) {
						// Ignore
					}
					params.put(key, value);
				}
			}
			
			return params;
		}
	}
}
