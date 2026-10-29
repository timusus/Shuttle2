package com.simplecityapps.shuttle.model

/** Which copy stands for a merged song when none is made primary. */
enum class PreferredCopy {
    /** A file on the device, then the best quality. */
    Local,

    /** The highest quality as it plays now, on the device or not. */
    BestQuality
}

/**
 * The user's say in which copy stands for a merged song, ahead of quality but never ahead of playing at all: copies made
 * primary by [Song.copyKey], albums made primary from one source by [Song.copyAlbumKey], then [preferred].
 */
data class SongCopyPreferences(
    val primaryCopies: Set<String> = emptySet(),
    val primaryAlbums: Set<String> = emptySet(),
    val preferred: PreferredCopy = PreferredCopy.Local
) {
    fun comparator(availability: SongCopyAvailability): Comparator<Song> {
        val primary = compareByDescending<Song> { it.copyKey in primaryCopies }.thenByDescending { it.copyAlbumKey in primaryAlbums }
        return if (preferred == PreferredCopy.BestQuality) primary.then(availability.quality) else primary
    }
}

/** A copy's source and its id there (a server's item id, else the file's path), which a re-sync keeps while row ids change. */
val Song.copyKey: String get() = "${mediaProvider.name}:${externalId ?: path}"

/** A copy's source and its album there: the server's album id, else the album's tags. */
val Song.copyAlbumKey: String get() = "${mediaProvider.name}:${serverAlbumId ?: "${albumArtist.orEmpty()}/${album.orEmpty()}"}"
