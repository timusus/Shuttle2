package com.simplecityapps.mediaprovider

import android.net.Uri
import com.simplecityapps.shuttle.model.Entry
import com.simplecityapps.shuttle.model.Song

/**
 * Matches m3u [Entry] locations back to library [Song]s by filename rather than full path
 * equality, since a device path recorded in the file may not round-trip exactly (drive letters,
 * SAF vs raw paths, or the file having moved since import). Shared by import
 * (`TaglibMediaProvider.findPlaylists`) and sync (`LocalPlaylistRepository.syncM3uFile`) so both
 * resolve the same entry the same way.
 */
object M3uEntryMatcher {
    fun sanitisedPathsByFilename(songs: List<Song>): Map<String, Song> = songs.associateBy { Uri.decode(it.path.substringAfterLast('/')).substringAfterLast(':') }

    fun match(
        entry: Entry,
        sanitisedSongPaths: Map<String, Song>
    ): Song? = sanitisedSongPaths.keys.firstOrNull { songPath ->
        when {
            songPath.equals(entry.location, ignoreCase = true) -> true
            songPath.length > entry.location.length -> songPath.contains(other = entry.location, ignoreCase = true)
            else -> entry.location.contains(other = songPath, ignoreCase = true)
        }
    }?.let { matchingPath -> sanitisedSongPaths[matchingPath] }
}
