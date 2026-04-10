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
import android.graphics.Bitmap;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import github.paroj.dsub2000.domain.ArtistInfo;
import github.paroj.dsub2000.domain.ChatMessage;
import github.paroj.dsub2000.domain.Genre;
import github.paroj.dsub2000.domain.Indexes;
import github.paroj.dsub2000.domain.InternetRadioStation;
import github.paroj.dsub2000.domain.Lyrics;
import github.paroj.dsub2000.domain.MusicDirectory;
import github.paroj.dsub2000.domain.MusicFolder;
import github.paroj.dsub2000.domain.PlayerQueue;
import github.paroj.dsub2000.domain.Playlist;
import github.paroj.dsub2000.domain.PodcastChannel;
import github.paroj.dsub2000.domain.RemoteStatus;
import github.paroj.dsub2000.domain.SearchCritera;
import github.paroj.dsub2000.domain.SearchResult;
import github.paroj.dsub2000.domain.Share;
import github.paroj.dsub2000.domain.User;
import github.paroj.dsub2000.util.ProgressListener;
import github.paroj.dsub2000.util.SilentBackgroundTask;

/**
 * Minimal MusicService implementation that sends HTTP requests directly to a remote
 * DSub2000 instance running LocalControlServer.
 *
 * Only implements jukebox control methods - all other methods throw UnsupportedOperationException.
 */
public class DirectHttpMusicService implements MusicService {
	private static final String TAG = DirectHttpMusicService.class.getSimpleName();

	private final String host;
	private final int port;

	public DirectHttpMusicService(String host, int port) {
		this.host = host;
		this.port = port;
	}

	private String sendHttpRequest(String endpoint) throws Exception {
		URL url = new URL("http://" + host + ":" + port + endpoint);
		Log.d(TAG, ">>> Sending request to: " + url);

		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(5000);

		int responseCode = connection.getResponseCode();
		Log.d(TAG, ">>> Response code: " + responseCode);
		if (responseCode != 200) {
			Log.e(TAG, ">>> HTTP request failed with code: " + responseCode);
			throw new Exception("HTTP request failed with code: " + responseCode);
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		reader.close();
		connection.disconnect();

		String result = response.toString();
		Log.d(TAG, ">>> Response: " + result);
		return result;
	}

	@Override
	public RemoteStatus updateJukeboxPlaylist(List<String> ids, Context context, ProgressListener progressListener) throws Exception {
		StringBuilder idsParam = new StringBuilder();
		for (int i = 0; i < ids.size(); i++) {
			if (i > 0) {
				idsParam.append(",");
			}
			idsParam.append(ids.get(i));
		}
		String xml = sendHttpRequest("/rest/jukeboxControl?action=set&id=" + idsParam.toString());
		return parseJukeboxStatus(xml);
	}

	/**
	 * Sends the full playlist to D2 with title/artist/album/duration metadata so that
	 * D2's now-playing screen can show real song information instead of raw IDs.
	 *
	 * Protocol: indexed query parameters — id0, title0, artist0, album0, duration0, suffix0, track0,
	 * id1, title1, … plus a "count" parameter so D2 knows how many songs to read.
	 *
	 * Both D1 (URLEncoder) and D2 (double URLDecoder via ServerProxy + parseQueryParams) handle
	 * UTF-8 encoded values correctly: URLEncoder encodes spaces as '+', the first decode
	 * (ServerProxy) is a no-op for '+', and the second decode (parseQueryParams) converts '+' → space.
	 */
	public RemoteStatus setPlaylistWithMetadata(List<MusicDirectory.Entry> songs, Context context, ProgressListener progressListener) throws Exception {
		StringBuilder url = new StringBuilder("/rest/jukeboxControl?action=set&count=");
		url.append(songs.size());
		for (int i = 0; i < songs.size(); i++) {
			MusicDirectory.Entry song = songs.get(i);
			url.append("&id").append(i).append("=").append(enc(song.getId()));
			if (song.getTitle()    != null) url.append("&title").append(i).append("=").append(enc(song.getTitle()));
			if (song.getArtist()   != null) url.append("&artist").append(i).append("=").append(enc(song.getArtist()));
			if (song.getAlbum()    != null) url.append("&album").append(i).append("=").append(enc(song.getAlbum()));
			if (song.getDuration() != null) url.append("&duration").append(i).append("=").append(song.getDuration());
			if (song.getSuffix()   != null) url.append("&suffix").append(i).append("=").append(enc(song.getSuffix()));
			if (song.getTrack()    != null) url.append("&track").append(i).append("=").append(song.getTrack());
		}
		String xml = sendHttpRequest(url.toString());
		return parseJukeboxStatus(xml);
	}

	private static String enc(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (Exception e) {
			return s;
		}
	}

	@Override
	public RemoteStatus skipJukebox(int index, int offsetSeconds, Context context, ProgressListener progressListener) throws Exception {
		String xml = sendHttpRequest("/rest/jukeboxControl?action=skip&index=" + index + "&offset=" + offsetSeconds);
		return parseJukeboxStatus(xml);
	}

	@Override
	public RemoteStatus stopJukebox(Context context, ProgressListener progressListener) throws Exception {
		String xml = sendHttpRequest("/rest/jukeboxControl?action=stop");
		return parseJukeboxStatus(xml);
	}

	@Override
	public RemoteStatus startJukebox(Context context, ProgressListener progressListener) throws Exception {
		String xml = sendHttpRequest("/rest/jukeboxControl?action=start");
		return parseJukeboxStatus(xml);
	}

	@Override
	public RemoteStatus getJukeboxStatus(Context context, ProgressListener progressListener) throws Exception {
		String xml = sendHttpRequest("/rest/jukeboxControl?action=status");
		return parseJukeboxStatus(xml);
	}

	@Override
	public RemoteStatus setJukeboxGain(float gain, Context context, ProgressListener progressListener) throws Exception {
		String xml = sendHttpRequest("/rest/jukeboxControl?action=setGain&gain=" + gain);
		return parseJukeboxStatus(xml);
	}

	/**
	 * Parses a Subsonic jukeboxStatus XML response into a RemoteStatus.
	 * Expected element: {@code <jukeboxStatus currentIndex="0" playing="true" gain="0.5" position="47"/>}
	 */
	private RemoteStatus parseJukeboxStatus(String xml) {
		RemoteStatus status = new RemoteStatus();
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(false);
			XmlPullParser parser = factory.newPullParser();
			parser.setInput(new StringReader(xml));

			int eventType = parser.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if (eventType == XmlPullParser.START_TAG && "jukeboxStatus".equals(parser.getName())) {
					String currentIndex = parser.getAttributeValue(null, "currentIndex");
					String playing     = parser.getAttributeValue(null, "playing");
					String gain        = parser.getAttributeValue(null, "gain");
					String position    = parser.getAttributeValue(null, "position");

					if (currentIndex != null) status.setCurrentIndex(Integer.parseInt(currentIndex));
					if (playing     != null) status.setPlaying(Boolean.parseBoolean(playing));
					if (gain        != null) status.setGain(Float.parseFloat(gain));
					if (position    != null) status.setPositionSeconds(Integer.parseInt(position));
					break;
				}
				eventType = parser.next();
			}
		} catch (Exception e) {
			Log.d(TAG, ">>> Failed to parse jukebox status XML: " + e);
		}
		return status;
	}

	// All other methods throw UnsupportedOperationException

	@Override
	public void ping(Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public boolean isLicenseValid(Context context, ProgressListener progressListener) throws Exception {
		return true;
	}

	@Override
	public List<MusicFolder> getMusicFolders(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void startRescan(Context context, ProgressListener listener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public Indexes getIndexes(String musicFolderId, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getMusicDirectory(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getArtist(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getAlbum(String id, String name, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public SearchResult search(SearchCritera criteria, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getStarredList(Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getPlaylist(boolean refresh, String id, String name, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public List<Playlist> getPlaylists(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public String createPlaylist(String id, String name, List<MusicDirectory.Entry> entries, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void deletePlaylist(String id, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void addToPlaylist(String id, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void removeFromPlaylist(String id, List<Integer> toRemove, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public String overwritePlaylist(String id, String name, List<MusicDirectory.Entry> toAdd, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void updatePlaylist(String id, String name, String comment, boolean pub, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public Lyrics getLyrics(String artist, String title, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void scrobble(String id, boolean submission, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getAlbumList(String type, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getAlbumList(String type, String extra, int size, int offset, boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getSongList(String type, int size, int offset, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getRandomSongs(int size, String artistId, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getRandomSongs(int size, String folder, String genre, String startYear, String endYear, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getRandomSongs(int size, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public String getCoverArtUrl(Context context, MusicDirectory.Entry entry) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public Bitmap getCoverArt(Context context, MusicDirectory.Entry entry, int size, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public HttpURLConnection getDownloadInputStream(Context context, MusicDirectory.Entry song, long offset, int maxBitrate, SilentBackgroundTask task) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public String getMusicUrl(Context context, MusicDirectory.Entry song, int maxBitrate) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public String getVideoUrl(int maxBitrate, Context context, String id) {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public String getVideoStreamUrl(String format, int maxBitrate, Context context, String id) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public String getHlsUrl(String id, int bitRate, Context context) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void setStarred(List<MusicDirectory.Entry> entries, List<MusicDirectory.Entry> artists, List<MusicDirectory.Entry> albums, boolean starred, ProgressListener progressListener, Context context) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public List<Share> getShares(Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getBookmarks(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void createBookmark(MusicDirectory.Entry entry, int position, String comment, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void deleteBookmark(MusicDirectory.Entry entry, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public User getUser(boolean refresh, String username, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public List<User> getUsers(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void createUser(User user, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void updateUser(User user, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void deleteUser(String username, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void changeEmail(String username, String email, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void changePassword(String username, String password, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public Bitmap getAvatar(String username, int size, Context context, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public ArtistInfo getArtistInfo(String id, boolean refresh, boolean allowNetwork, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public Bitmap getBitmap(String url, int size, Context context, ProgressListener progressListener, SilentBackgroundTask task) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getVideos(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void savePlayQueue(List<MusicDirectory.Entry> songs, MusicDirectory.Entry currentPlaying, int position, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public PlayerQueue getPlayQueue(Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public List<InternetRadioStation> getInternetRadioStations(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public List<ChatMessage> getChatMessages(Long since, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void addChatMessage(String message, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public List<Genre> getGenres(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getSongsByGenre(String genre, int count, int offset, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getTopTrackSongs(String artist, int size, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public List<PodcastChannel> getPodcastChannels(boolean refresh, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getPodcastEpisodes(boolean refresh, String id, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public MusicDirectory getNewestPodcastEpisodes(boolean refresh, Context context, ProgressListener progressListener, int count) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void refreshPodcasts(Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void createPodcastChannel(String url, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void deletePodcastChannel(String id, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void downloadPodcastEpisode(String id, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void deletePodcastEpisode(String id, String parent, ProgressListener progressListener, Context context) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void setRating(MusicDirectory.Entry entry, int rating, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public List<Share> createShare(List<String> ids, String description, Long expires, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void deleteShare(String id, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public void updateShare(String id, String description, Long expires, Context context, ProgressListener progressListener) throws Exception {
		throw new UnsupportedOperationException("DirectHttpMusicService only supports jukebox control");
	}

	@Override
	public int processOfflineSyncs(Context context, ProgressListener progressListener) throws Exception {
		return 0;
	}

	@Override
	public void setInstance(Integer instance) throws Exception {
		// No-op
	}
}
