package github.paroj.dsub2000.service.parser;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

import java.io.StringReader;

import github.paroj.dsub2000.domain.MusicDirectory;
import github.paroj.dsub2000.domain.SearchResult;

/** Pins how OpenSubsonic artist containers are read without disturbing their owners. */
public class MultiArtistParserTest extends TestCase {

	public void testAlbumListReadsDisplayArtistAndArtists() throws Exception {
		String xml = response("<albumList2>"
				+ "<album id=\"album-1\" title=\"Album\" artist=\"Legacy\" displayArtist=\"A &amp; B\" isDir=\"true\">"
				+ artists("artist-1", "A", "artist-2", "B")
				+ "</album>"
				+ "</albumList2>");

		MusicDirectory.Entry album = parseEntryList(xml).getChildren().get(0);

		assertEquals("A & B", album.getDisplayArtist());
		assertEquals(2, album.getArtists().size());
		assertEquals("A", album.getArtists().get(0).getName());
		assertEquals("B", album.getArtists().get(1).getName());
	}

	public void testDisplayArtistFallsBackToJoinedArtistNames() throws Exception {
		String xml = response("<albumList2>"
				+ "<album id=\"album-1\" title=\"Album\" artist=\"Legacy\" isDir=\"true\">"
				+ artists("artist-1", "A", "artist-2", "B")
				+ "</album>"
				+ "</albumList2>");

		MusicDirectory.Entry album = parseEntryList(xml).getChildren().get(0);

		assertEquals("A, B", album.getDisplayArtist());
	}

	public void testLegacyArtistIsUsedWhenOpenSubsonicFieldsAreMissing() throws Exception {
		String xml = response("<albumList2>"
				+ "<album id=\"album-1\" title=\"Album\" artist=\"Legacy\" isDir=\"true\"/>"
				+ "</albumList2>");

		MusicDirectory.Entry album = parseEntryList(xml).getChildren().get(0);

		assertEquals("Legacy", album.getDisplayArtist());
	}

	public void testSearchDoesNotTreatNestedArtistsAsResults() throws Exception {
		String xml = response("<searchResult3>"
				+ "<artist id=\"artist-9\" name=\"Top level\"/>"
				+ "<album id=\"album-1\" title=\"Album\" artist=\"Legacy\" isDir=\"true\">"
				+ artists("artist-1", "A", "artist-2", "B")
				+ "</album>"
				+ "<song id=\"track-1\" title=\"Song\" artist=\"Legacy\" displayArtist=\"A &amp; B\">"
				+ artists("artist-1", "A", "artist-2", "B")
				+ "</song>"
				+ "</searchResult3>");

		SearchResult result = parseSearch(xml);

		assertEquals(1, result.getArtists().size());
		assertEquals("Top level", result.getArtists().get(0).getName());
		assertEquals(2, result.getAlbums().get(0).getArtists().size());
		assertEquals("A & B", result.getSongs().get(0).getDisplayArtist());
		assertEquals(2, result.getSongs().get(0).getArtists().size());
	}

	public void testAlbumArtistsDoNotOverwriteTheAlbumIdentity() throws Exception {
		String xml = response("<album id=\"album-1\" name=\"Album\" artist=\"Legacy\">"
				+ artists("artist-1", "A", "artist-2", "B")
				+ "<song id=\"track-1\" title=\"Song\" artist=\"Legacy\" displayArtist=\"A &amp; B\">"
				+ artists("artist-1", "A", "artist-2", "B")
				+ "</song>"
				+ "</album>");

		MusicDirectory dir = new MusicDirectoryParser(context(), 0).parse(null, new StringReader(xml), null);

		assertEquals("album-1", dir.getId());
		assertEquals("Album", dir.getName());
		assertEquals("A & B", dir.getChildren().get(0).getDisplayArtist());
	}

	private MusicDirectory parseEntryList(String xml) throws Exception {
		return new EntryListParser(context(), 0).parse(new StringReader(xml), null);
	}

	private SearchResult parseSearch(String xml) throws Exception {
		return new SearchResult2Parser(context(), 0).parse(new StringReader(xml), null);
	}

	private android.content.Context context() {
		return InstrumentationRegistry.getInstrumentation().getTargetContext();
	}

	private static String artists(String... idsAndNames) {
		StringBuilder xml = new StringBuilder("<artists>");
		for (int i = 0; i < idsAndNames.length; i += 2) {
			xml.append("<artist id=\"").append(idsAndNames[i]).append("\" name=\"")
					.append(idsAndNames[i + 1]).append("\"/>");
		}
		return xml.append("</artists>").toString();
	}

	private static String response(String body) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
				+ "<subsonic-response xmlns=\"http://subsonic.org/restapi\" status=\"ok\" version=\"1.16.1\">"
				+ body
				+ "</subsonic-response>";
	}
}
