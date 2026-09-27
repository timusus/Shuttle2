package com.simplecityapps.fakes

import com.simplecityapps.shuttle.ui.screens.library.ViewMode
import com.simplecityapps.shuttle.ui.screens.library.albumartists.ArtistListPreferences

class FakeArtistListPreferences : ArtistListPreferences {
    override var artistListViewMode: ViewMode = ViewMode.List
}
