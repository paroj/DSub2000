package github.paroj.dsub2000.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import github.paroj.dsub2000.domain.MusicDirectory;
import github.paroj.dsub2000.util.FileUtil;
import github.paroj.dsub2000.util.Util;

import org.junit.Test;

import java.io.File;

public class DownloadFileTest {

	private static MusicDirectory.Entry song(String folder) {
		MusicDirectory.Entry song = new MusicDirectory.Entry(folder);
		song.setPath(folder + "/Album/01-Track.mp3");
		song.setTitle("Track");
		song.setArtist("Lazy");
		song.setAlbum("Album");
		song.setTrack(1);
		song.setDiscNumber(1);
		song.setSuffix("mp3");
		song.setTranscodedSuffix("mp3");
		song.setDirectory(false);
		return song;
	}

	@Test
	public void songPathsFollowServerLayout() {
		Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		String folder = "layout-" + System.nanoTime();
		File albumDirectory = new File(FileUtil.getMusicDirectory(context), folder + "/Album");

		DownloadFile downloadFile = new DownloadFile(context, song(folder), false);

		assertEquals(new File(albumDirectory, "01-001-Track.mp3"), downloadFile.getSaveFile());
		assertEquals(new File(albumDirectory, "01-001-Track.partial.mp3"), downloadFile.getPartialFile());
		// Neither file exists, so the complete file falls back to the save path.
		assertEquals(new File(albumDirectory, "01-001-Track.mp3"), downloadFile.getCompleteFile());
	}

	@Test
	public void existingLegacyFileNameIsKept() throws Exception {
		Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		String folder = "legacy-" + System.nanoTime();
		File albumDirectory = new File(FileUtil.getMusicDirectory(context), folder + "/Album");
		File legacy = new File(albumDirectory, "01-Track.mp3");
		assertTrue(albumDirectory.mkdirs());
		assertTrue(legacy.createNewFile());
		try {
			DownloadFile downloadFile = new DownloadFile(context, song(folder), false);

			assertEquals(legacy, downloadFile.getSaveFile());
			assertEquals(new File(albumDirectory, "01-001-Track.partial.mp3"), downloadFile.getPartialFile());
			assertEquals(legacy, downloadFile.getCompleteFile());
		} finally {
			assertTrue(legacy.delete());
			assertTrue(albumDirectory.delete());
			assertTrue(albumDirectory.getParentFile().delete());
		}
	}

	@Test
	public void offlineEntriesKeepOriginalPath() throws Exception {
		Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		File original = new File(FileUtil.getMusicDirectory(context), "offline-" + System.nanoTime() + ".mp3");
		assertTrue(original.createNewFile());

		MusicDirectory.Entry song = new MusicDirectory.Entry(original.getAbsolutePath());
		song.setTitle("Offline");
		song.setArtist("Lazy");
		song.setAlbum("Album");
		song.setSuffix("mp3");
		song.setDirectory(false);

		boolean wasOffline = Util.isOffline(context);
		try {
			Util.setOffline(context, true);

			assertEquals(original, FileUtil.getSongFiles(context, song).save());
			assertEquals(original, new DownloadFile(context, song, false).getSaveFile());
		} finally {
			Util.setOffline(context, wasOffline);
			assertTrue(original.delete());
		}
	}
}
