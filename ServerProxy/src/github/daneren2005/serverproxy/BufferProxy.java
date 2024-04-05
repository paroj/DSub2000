/*
This file is part of ServerProxy.
SocketProxy is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
Subsonic is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.
You should have received a copy of the GNU General Public License
along with Subsonic. If not, see <http://www.gnu.org/licenses/>.
Copyright 2014 (C) Scott Jackson
*/

package github.daneren2005.serverproxy;

import java.io.File;
import java.net.Socket;

import android.content.Context;

public class BufferProxy extends FileProxy {
	private static final String TAG = BufferProxy.class.getSimpleName();
	protected BufferFile progress;

	public BufferProxy(Context context) {
		super(context);
	}

	protected ProxyTask getTask(Socket client) {
		return new BufferFileTask(client);
	}

	public void setBufferFile(BufferFile progress) {
		this.progress = progress;
	}

	protected class BufferFileTask extends StreamFileTask {
		public BufferFileTask(Socket client) {
			super(client);
		}

		@Override
		File getFile(String path) {
			return progress.getFile();
		}

		@Override
		Long getContentLength() {
			Long contentLength = progress.getContentLength();
			if(contentLength == null && progress.isWorkDone()) {
				contentLength = file.length();
			}
			return contentLength;
		}
		@Override
		long getFileSize() {
			return progress.getEstimatedSize();
		}

		@Override
		public void onStart() {
			progress.onStart();
		}
		@Override
		public void onStop() {
			progress.onStop();
		}
		@Override
		public void onResume() {
			progress.onResume();
		}

		@Override
		public boolean isWorkDone() {
			return progress.isWorkDone() && cbSkip >= file.length();
		}
	}
}
