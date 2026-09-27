package com.simplecityapps.fakes

import com.simplecityapps.shuttle.ui.screens.library.LibraryViewPreferences

fun fakeLibraryViewPreferences(
    sort: FakeSortPreferences = FakeSortPreferences(),
    albumList: FakeAlbumListPreferences = FakeAlbumListPreferences(),
    artistList: FakeArtistListPreferences = FakeArtistListPreferences(),
) = LibraryViewPreferences(sort, albumList, artistList)
