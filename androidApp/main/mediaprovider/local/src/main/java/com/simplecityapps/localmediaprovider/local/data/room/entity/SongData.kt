package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import java.util.*

@Entity(
    tableName = "songs",
    indices = [
        Index("path", unique = true)
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
    @ColumnInfo(name = "lastModified") var lastModified: Date,
    @ColumnInfo(name = "playbackPosition") var playbackPosition: Int = 0,
    @ColumnInfo(name = "playCount") var playCount: Int = 0,
    @ColumnInfo(name = "lastPlayed") var lastPlayed: Date? = null,
    @ColumnInfo(name = "lastCompleted") var lastCompleted: Date? = null,
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
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}

fun Song.toSongData(mediaProviderType: MediaProviderType): SongData {
    return SongData(
        name = name,
        track = track,
        disc = disc,
        duration = duration ?: 0,
        year = date?.year,
        genres = genres,
        path = path,
        albumArtist = albumArtist,
        artists = artists,
        album = album,
        size = size ?: 0L,
        mimeType = mimeType ?: "audio/*",
        lastModified = dateModified?.let { Date(it.toEpochMilliseconds()) } ?: Date(),
        playbackPosition = playbackPosition,
        playCount = playCount,
        lastPlayed = lastPlayed?.let { Date(it.toEpochMilliseconds()) },
        lastCompleted = lastCompleted?.let { Date(it.toEpochMilliseconds()) },
        excluded = false,
        externalId = externalId,
        mediaProvider = mediaProviderType,
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum,
        lyrics = lyrics,
        grouping = grouping,
        bitRate = bitRate,
        bitDepth = null,
        sampleRate = sampleRate,
        channelCount = channelCount
    ).apply {
        id = this@toSongData.id
    }
}

fun List<Song>.toSongData(mediaProviderType: MediaProviderType): List<SongData> {
    return map { song -> song.toSongData(mediaProviderType) }
}