package com.simplecityapps.fakes

import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.ui.actions.ObserveDownloadedSongs
import com.simplecityapps.shuttle.ui.actions.ObserveSmartPlaylistSongs
import com.simplecityapps.shuttle.ui.actions.ObserveSongs
import com.simplecityapps.shuttle.ui.actions.RankedAlbumKeys

/** [ObserveSmartPlaylistSongs] over [songRepository] and [offline]; the ranked playlists list no albums unless [rankedAlbumKeys] says. */
fun fakeObserveSmartPlaylistSongs(
    songRepository: SongRepository,
    offline: FakeOfflineSongs = FakeOfflineSongs(),
    rankedAlbumKeys: RankedAlbumKeys = RankedAlbumKeys { emptyList() },
) = ObserveSmartPlaylistSongs(
    ObserveSongs(songRepository, offline.filter),
    ObserveDownloadedSongs(songRepository, offline.statusSource),
    rankedAlbumKeys,
)
