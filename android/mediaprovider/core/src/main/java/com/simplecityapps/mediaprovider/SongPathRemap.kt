package com.simplecityapps.mediaprovider

/**
 * Moves a song that a provider stored under an identity it no longer produces to the path it produces now. The song
 * keeps its row, so its play count, last played date, exclude flag, playlist entries and place in the saved queue all
 * survive the next import, which then matches the song by its new path.
 *
 * [duplicateIds] are other rows for the same file (the old SAF scanner stored a file once per overlapping folder
 * grant). Their playlist entries move to [songId]; the rows themselves keep their old path, so the import removes them.
 */
data class SongPathRemap(
    val songId: Long,
    val path: String,
    val duplicateIds: List<Long> = emptyList()
)
