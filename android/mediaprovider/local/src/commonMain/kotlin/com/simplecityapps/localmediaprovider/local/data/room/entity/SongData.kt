package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["path", "mediaProvider"], unique = true)
    ]
)
data class SongData(
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "track") val track: Int?,
    @ColumnInfo(name = "disc") val disc: Int?,
    @ColumnInfo(name = "duration") val duration: Int,
    @ColumnInfo(name = "year") val year: Int?,
    @ColumnInfo(name = "genres") val genres: List<String> = emptyList(),
    @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "albumArtist") var albumArtist: String?,
    @ColumnInfo(name = "artists") var artists: List<String> = emptyList(),
    @ColumnInfo(name = "album") var album: String?,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "mimeType") var mimeType: String,
    @ColumnInfo(name = "lastModified") var lastModified: Instant,
    @ColumnInfo(name = "playbackPosition") var playbackPosition: Int = 0,
    @ColumnInfo(name = "playCount") var playCount: Int = 0,
    @ColumnInfo(name = "lastPlayed") var lastPlayed: Instant? = null,
    @ColumnInfo(name = "lastCompleted") var lastCompleted: Instant? = null,
    @ColumnInfo(name = "blacklisted") var excluded: Boolean = false,
    @ColumnInfo(name = "externalId") var externalId: String? = null,
    @ColumnInfo(name = "mediaProvider") var mediaProvider: MediaProviderType = MediaProviderType.Shuttle,
    @ColumnInfo(name = "replayGainTrack") var replayGainTrack: Double? = null,
    @ColumnInfo(name = "replayGainAlbum") var replayGainAlbum: Double? = null,
    @ColumnInfo(name = "lyrics") var lyrics: String?,
    @ColumnInfo(name = "grouping") var grouping: String?,
    @ColumnInfo(name = "bitRate") var bitRate: Int?,
    @ColumnInfo(name = "bitDepth") var bitDepth: Int?,
    @ColumnInfo(name = "sampleRate") var sampleRate: Int?,
    @ColumnInfo(name = "channelCount") var channelCount: Int?,
    // The source audio codec, when the provider's metadata carries one. See [Song.audioCodec].
    @ColumnInfo(name = "audioCodec") var audioCodec: String? = null,
    @ColumnInfo(name = "artworkVersion") var artworkVersion: String? = null,
    // The server's date for a remote song; for a local one, stamped on first import and carried through rescans and tag
    // edits by the importer's diff. Nullable only so the migration to version 45 could add it without a default: every
    // row has one, backfilled from lastModified.
    @ColumnInfo(name = "dateAdded") var dateAdded: Instant? = null,
    // When the song was made a favourite; null when it isn't one. Left out of [SongDataUpdate], so a rescan or a remote
    // sync keeps it, and written only by [com.simplecityapps.localmediaprovider.local.data.room.dao.SongDataDao.setFavourite].
    @ColumnInfo(name = "favouritedAt") var favouritedAt: Instant? = null
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}

fun Song.toSongData(mediaProviderType: MediaProviderType): SongData = SongData(
    name = name,
    track = track,
    disc = disc,
    duration = duration,
    year = date?.year,
    genres = genres,
    path = path,
    albumArtist = albumArtist,
    artists = artists,
    album = album,
    size = size,
    mimeType = mimeType,
    lastModified = lastModified ?: Clock.System.now(),
    playbackPosition = playbackPosition,
    playCount = playCount,
    lastPlayed = lastPlayed,
    lastCompleted = lastCompleted,
    excluded = false,
    externalId = externalId,
    mediaProvider = mediaProviderType,
    replayGainTrack = replayGainTrack,
    replayGainAlbum = replayGainAlbum,
    lyrics = lyrics,
    grouping = grouping,
    bitRate = bitRate,
    bitDepth = bitDepth,
    sampleRate = sampleRate,
    channelCount = channelCount,
    audioCodec = audioCodec,
    artworkVersion = artworkVersion,
    dateAdded = resolvedDateAdded(),
    favouritedAt = favouritedAt
).apply {
    id = this@toSongData.id
}

/**
 * When the song was added: the provider's date (a remote server's) when it has one. Otherwise, when a song first reaches
 * the library, the file's modification time, as the migration to version 45 backfilled for the songs already there, so
 * a first scan doesn't make the whole library "recently added". A date in the future (a wrong clock) counts as now.
 */
private fun Song.resolvedDateAdded(): Instant {
    val now = Clock.System.now()
    val added = dateAdded ?: lastModified ?: return now
    return if (added > now) now else added
}

fun List<Song>.toSongData(mediaProviderType: MediaProviderType): List<SongData> = map { song -> song.toSongData(mediaProviderType) }
