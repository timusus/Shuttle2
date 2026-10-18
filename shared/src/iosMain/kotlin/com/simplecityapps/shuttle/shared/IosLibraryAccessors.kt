package com.simplecityapps.shuttle.shared

import com.simplecityapps.localmediaprovider.local.favourites.FavouriteSender
import com.simplecityapps.mediaprovider.SongImportStateProvider
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.shuttle.model.AlbumIndexProvider
import com.simplecityapps.shuttle.shared.artwork.ArtworkUrls
import com.simplecityapps.shuttle.shared.intents.AppIntentLibrary
import com.simplecityapps.shuttle.shared.intents.VoiceLibrary
import com.simplecityapps.shuttle.shared.platform.BackgroundSync
import com.simplecityapps.shuttle.ui.screens.search.LibrarySearchIndex
import com.simplecityapps.shuttle.ui.screens.sources.MediaSources

/** The library, its indexes and import, and the system surfaces that read it; part of [IosAppGraph]. */
interface IosLibraryAccessors {
    /** Sends favourites to their servers; Swift starts it once, at launch. */
    val favouriteSender: FavouriteSender

    /** The album index; making it starts its build in the background, so Swift reads it at launch, ahead of the first content that needs it. */
    val albumIndexProvider: AlbumIndexProvider

    /** The search index; Swift warms it once, at launch, so the first search doesn't wait for the build. */
    val librarySearchIndex: LibrarySearchIndex

    /** The library's providers and the import that fills it: the Library's pull-to-refresh and launch import. */
    val mediaSources: MediaSources

    /** The daily background sync, which Swift's `BGAppRefreshTask` runs. */
    val backgroundSync: BackgroundSync

    /** The running import's progress, for the Library root. */
    val songImportStateProvider: SongImportStateProvider

    /** Authenticated artwork urls for songs, albums and album artists; Swift's `ArtworkLoader` fetches and decodes. */
    val artworkUrls: ArtworkUrls

    /** The playlists and plays Siri, Shortcuts and Spotlight's App Intents offer. */
    val appIntentLibrary: AppIntentLibrary

    /** The library, for tests to seed songs into the graph's in-memory database. */
    val songRepository: SongRepository

    /** What Siri's media domain searches and plays (`INPlayMediaIntent`). */
    val voiceLibrary: VoiceLibrary
}
