package github.paroj.dsub2000.domain.repository;

import android.content.Context;

import java.util.List;

import github.paroj.dsub2000.domain.MusicDirectory;
import github.paroj.dsub2000.service.MusicService;
import github.paroj.dsub2000.util.ProgressListener;

public final class StarredUtil {
    public static MusicDirectory getStarredAlbumsList(MusicService service, Context context, ProgressListener progressListener) throws Exception {
        return getStarredFilteredList(service, context, progressListener, true, false);
    }

    public static MusicDirectory getStarredSongsList(MusicService service, Context context, ProgressListener progressListener) throws Exception {
        return getStarredFilteredList(service, context, progressListener, false, true);
    }

    private static MusicDirectory getStarredFilteredList(MusicService service, Context context, ProgressListener progressListener, boolean includeDirs, boolean includeFiles) throws Exception {
        MusicDirectory result = service.getStarredList(context, progressListener);
        List<MusicDirectory.Entry> children = result.getChildren(includeDirs, includeFiles);
        return new MusicDirectory(children);
    }
}
