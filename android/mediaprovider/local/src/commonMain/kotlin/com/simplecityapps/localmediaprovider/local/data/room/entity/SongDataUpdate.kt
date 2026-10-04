package com.simplecityapps.localmediaprovider.local.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant

data class SongDataUpdate(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String?,
    @ColumnInfo(name = "track") val track: Int?,
    @ColumnInfo(name = "disc") val disc: Int?,
    @ColumnInfo(name = "duration") val duration: Int,
    @ColumnInfo(name = "year") val year: Int?,
    @ColumnInfo(name = "genres") val genres: List<String>,
    @ColumnInfo(name = "albumArtist") var albumArtist: String?,
    @ColumnInfo(name = "artists") var artists: List<String>,
    @ColumnInfo(name = "album") var album: String?,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "mimeType") var mimeType: String,
    @ColumnInfo(name = "lastModified") var lastModified: Instant,
    @ColumnInfo(name = "externalId") var externalId: String? = null,
    @ColumnInfo(name = "replayGainTrack") var replayGainTrack: Double? = null,
    @ColumnInfo(name = "replayGainAlbum") var replayGainAlbum: Double? = null,
    @ColumnInfo(name = "lyrics") var lyrics: String? = null,
    @ColumnInfo(name = "grouping") var grouping: String? = null,
    @ColumnInfo(name = "artworkVersion") var artworkVersion: String? = null,
    // Written on update too, so a remote song's server date replaces an older import stamp. The importer's diff carries
    // the stored value over when the provider has none, so a local rescan or tag edit leaves it where it was.
    @ColumnInfo(name = "dateAdded") var dateAdded: Instant? = null,
    // Written on every update, so a rescan or sync fills them in place for a song stored before they existed (#637)
    @ColumnInfo(name = "albumArtists") var albumArtists: List<String>? = null,
    @ColumnInfo(name = "artistsTag") var artistsTag: List<String>? = null,
    @ColumnInfo(name = "artistDisplay") var artistDisplay: String? = null,
    @ColumnInfo(name = "compilation") var compilation: Boolean? = null,
    @ColumnInfo(name = "mbTrackId") var mbTrackId: String? = null,
    @ColumnInfo(name = "mbAlbumId") var mbAlbumId: String? = null,
    @ColumnInfo(name = "mbReleaseGroupId") var mbReleaseGroupId: String? = null,
    @ColumnInfo(name = "mbArtistIds") var mbArtistIds: List<String>? = null,
    @ColumnInfo(name = "mbAlbumArtistIds") var mbAlbumArtistIds: List<String>? = null,
    @ColumnInfo(name = "serverAlbumId") var serverAlbumId: String? = null,
    @ColumnInfo(name = "serverArtistIds") var serverArtistIds: List<String>? = null,
    @ColumnInfo(name = "serverAlbumArtistIds") var serverAlbumArtistIds: List<String>? = null,
    // Written on every update, so a rescan or sync fills it in for a song stored before providers reported it (#798)
    @ColumnInfo(name = "bitDepth") var bitDepth: Int? = null
)

fun SongData.toSongDataUpdate(): SongDataUpdate = SongDataUpdate(
    id = id,
    name = name,
    track = track,
    disc = disc,
    duration = duration,
    year = year,
    genres = genres,
    albumArtist = albumArtist,
    artists = artists,
    album = album,
    size = size,
    mimeType = mimeType,
    lastModified = lastModified,
    externalId = externalId,
    replayGainTrack = replayGainTrack,
    replayGainAlbum = replayGainAlbum,
    lyrics = lyrics,
    grouping = grouping,
    artworkVersion = artworkVersion,
    dateAdded = dateAdded,
    albumArtists = albumArtists,
    artistsTag = artistsTag,
    artistDisplay = artistDisplay,
    compilation = compilation,
    mbTrackId = mbTrackId,
    mbAlbumId = mbAlbumId,
    mbReleaseGroupId = mbReleaseGroupId,
    mbArtistIds = mbArtistIds,
    mbAlbumArtistIds = mbAlbumArtistIds,
    serverAlbumId = serverAlbumId,
    serverArtistIds = serverArtistIds,
    serverAlbumArtistIds = serverAlbumArtistIds,
    bitDepth = bitDepth
)

fun Song.toSongDataUpdate(): SongDataUpdate = toSongData(MediaProviderType.Shuttle).toSongDataUpdate()

fun List<Song>.toSongDataUpdate(): List<SongDataUpdate> = map { song -> song.toSongDataUpdate() }
