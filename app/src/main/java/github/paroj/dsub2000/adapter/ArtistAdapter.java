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
	Copyright 2015 (C) Scott Jackson
*/

package github.paroj.dsub2000.adapter;

import android.content.Context;
import android.content.DialogInterface;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import github.paroj.dsub2000.R;
import github.paroj.dsub2000.domain.Artist;
import github.paroj.dsub2000.domain.MusicDirectory.Entry;
import github.paroj.dsub2000.domain.MusicFolder;
import github.paroj.dsub2000.util.Util;
import github.paroj.dsub2000.view.ArtistView;
import github.paroj.dsub2000.view.FastScroller;
import github.paroj.dsub2000.view.SongView;
import github.paroj.dsub2000.view.UpdateView;

public class ArtistAdapter extends SectionAdapter<Serializable> implements FastScroller.BubbleTextGetter {
	public static int VIEW_TYPE_SONG = 3;
	public static int VIEW_TYPE_ARTIST = 4;

	private List<MusicFolder> musicFolders;
	private OnMusicFoldersChanged onMusicFoldersChanged;

	public ArtistAdapter(Context context, List<Serializable> artists, OnItemClickedListener listener) {
		this(context, artists, null, listener, null);
	}

	public ArtistAdapter(Context context, List<Serializable> artists, List<MusicFolder> musicFolders, OnItemClickedListener onItemClickedListener, OnMusicFoldersChanged onMusicFoldersChanged) {
		super(context, artists);
		this.musicFolders = musicFolders;
		this.onItemClickedListener = onItemClickedListener;
		this.onMusicFoldersChanged = onMusicFoldersChanged;

		if(musicFolders != null) {
			this.singleSectionHeader = true;
		}
	}

	@Override
	public UpdateView.UpdateViewHolder onCreateHeaderHolder(ViewGroup parent) {
		final View header = LayoutInflater.from(context).inflate(R.layout.select_artist_header, parent, false);
		header.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showLibraryFilterDialog(context, musicFolders, onMusicFoldersChanged);
			}
		});

		return new UpdateView.UpdateViewHolder(header, false);
	}

	public static void showLibraryFilterDialog(final Context context, final List<MusicFolder> musicFolders, final OnMusicFoldersChanged callback) {
		if(musicFolders == null || musicFolders.isEmpty()) {
			return;
		}

		final String[] names = new String[musicFolders.size()];
		final boolean[] checked = new boolean[musicFolders.size()];
		Set<String> selected = new HashSet<>(Util.getSelectedMusicFolderIds(context));
		for(int i = 0; i < musicFolders.size(); i++) {
			names[i] = musicFolders.get(i).getName();
			checked[i] = selected.contains(musicFolders.get(i).getId());
		}

		new AlertDialog.Builder(context)
			.setTitle(R.string.library_filter_dialog_title)
			.setMultiChoiceItems(names, checked, new DialogInterface.OnMultiChoiceClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which, boolean isChecked) {
					checked[which] = isChecked;
				}
			})
			.setNeutralButton(R.string.select_artist_all_folders, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if(callback != null) {
						callback.onMusicFoldersChanged(new ArrayList<MusicFolder>());
					}
				}
			})
			.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					List<MusicFolder> result = new ArrayList<>();
					for(int i = 0; i < musicFolders.size(); i++) {
						if(checked[i]) result.add(musicFolders.get(i));
					}
					if(callback != null) {
						callback.onMusicFoldersChanged(result);
					}
				}
			})
			.setNegativeButton(R.string.common_cancel, null)
			.show();
	}

	@Override
	public void onBindHeaderHolder(UpdateView.UpdateViewHolder holder, String header, int sectionIndex) {
		TextView folderName = (TextView) holder.getView().findViewById(R.id.select_artist_folder_2);

		List<String> selectedIds = Util.getSelectedMusicFolderIds(context);
		if(selectedIds.isEmpty()) {
			folderName.setText(R.string.select_artist_all_folders);
		} else if(selectedIds.size() == 1) {
			String only = selectedIds.get(0);
			String resolved = only;
			for(MusicFolder musicFolder : musicFolders) {
				if(musicFolder.getId().equals(only)) {
					resolved = musicFolder.getName();
					break;
				}
			}
			folderName.setText(resolved);
		} else {
			folderName.setText(context.getString(R.string.select_artist_n_folders, selectedIds.size()));
		}
	}

	@Override
	public UpdateView.UpdateViewHolder onCreateSectionViewHolder(ViewGroup parent, int viewType) {
		UpdateView updateView = null;
		if(viewType == VIEW_TYPE_ARTIST) {
			updateView = new ArtistView(context);
		} else if(viewType == VIEW_TYPE_SONG) {
			updateView = new SongView(context);
		}

		return new UpdateView.UpdateViewHolder(updateView);
	}

	@Override
	public void onBindViewHolder(UpdateView.UpdateViewHolder holder, Serializable item, int viewType) {
		UpdateView view = holder.getUpdateView();
		if(viewType == VIEW_TYPE_ARTIST) {
			view.setObject(item);
		} else if(viewType == VIEW_TYPE_SONG) {
			SongView songView = (SongView) view;
			Entry entry = (Entry) item;
			songView.setObject(entry, checkable && !entry.isVideo());
		}
	}

	@Override
	public int getItemViewType(Serializable item) {
		if(item instanceof Artist) {
			return VIEW_TYPE_ARTIST;
		} else {
			return VIEW_TYPE_SONG;
		}
	}

	@Override
	public String getTextToShowInBubble(int position) {
		Object item = getItemForPosition(position);
		if(item instanceof Artist) {
			return getNameIndex(((Artist) item).getName(), true);
		} else {
			return null;
		}
	}

	public interface OnMusicFoldersChanged {
		void onMusicFoldersChanged(List<MusicFolder> selectedFolders);
	}
}
