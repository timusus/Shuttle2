package com.simplecityapps.shuttle.ui.screens.tageditor

import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.model.Song

/** The system's request for the user's consent to change some songs' files; an opaque marker the platform launches. */
interface WriteConsent

/** Reads and writes a song file's tags: the tag editor's only way to the file system. */
interface TagFileAccess {
    /** The tags in [song]'s file, or null if the file can't be read or isn't one the editor can write. */
    suspend fun read(song: Song): AudioFile?

    /**
     * The system's request for the user's consent to change [songs]' files, or null when S2 can already write them all.
     * Launch it before [write]; declined, the writes fail.
     */
    suspend fun writeConsent(songs: List<Song>): WriteConsent?

    /**
     * Writes [metadata] (TagLib property keys to values) to [song]'s file.
     *
     * @return whether the write succeeded.
     */
    suspend fun write(
        song: Song,
        metadata: Map<String, List<String>>,
    ): Boolean
}
