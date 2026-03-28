package com.simplecityapps.fakes

import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.albums.AlbumListPreferences

class FakeAlbumListPreferences : AlbumListPreferences {
    override var albumListViewMode: ViewMode = ViewMode.List
}
