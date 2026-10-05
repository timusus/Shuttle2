package com.simplecityapps.localmediaprovider.local.provider

import com.simplecityapps.ktaglib.KTagLib
import com.simplecityapps.mediaprovider.model.AudioFile
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.Song
import kotlin.time.Instant

fun AudioFile.toSong(
    providerType: MediaProviderType,
    folderImages: Collection<FolderImage>
): Song = Song(
    id = 0,
    name = title,
    artists = artists,
    albumArtist = albumArtist,
    album = album,
    track = track,
    disc = disc,
    duration = duration ?: 0,
    date = year?.toYearDate(),
    genres = genres,
    path = path,
    size = size,
    mimeType = mimeType,
    lastModified = Instant.fromEpochMilliseconds(lastModified),
    lastPlayed = null,
    lastCompleted = null,
    playCount = 0,
    playbackPosition = 0,
    blacklisted = false,
    mediaProvider = providerType,
    replayGainTrack = replayGainTrack,
    replayGainAlbum = replayGainAlbum,
    lyrics = lyrics,
    grouping = grouping,
    bitRate = bitRate,
    bitDepth = bitDepth,
    sampleRate = sampleRate,
    channelCount = channelCount,
    artworkVersion = localArtworkVersion(lastModified, folderImages),
    albumArtists = albumArtists,
    artistsTag = artistsTag,
    artistDisplay = artistDisplay,
    compilation = compilation,
    mbTrackId = mbTrackId,
    mbAlbumId = mbAlbumId,
    mbReleaseGroupId = mbReleaseGroupId,
    mbArtistIds = mbArtistIds,
    mbAlbumArtistIds = mbAlbumArtistIds
)

fun KTagLib.getAudioFile(
    fileDescriptor: Int,
    filePath: String,
    fileName: String,
    lastModified: Long,
    size: Long,
    mimeType: String?
): AudioFile {
    val metadata = getMetadata(fileDescriptor, fileName)
    val tags = metadata?.propertyMap.orEmpty().toFileTags()
    return AudioFile(
        path = filePath,
        size = size,
        lastModified = lastModified,
        mimeType = mimeType ?: "audio/*",
        title = tags.title ?: fileName.substringBeforeLast("."),
        albumArtist = tags.albumArtist,
        artists = tags.artists,
        album = tags.album,
        track = tags.track,
        trackTotal = tags.trackTotal,
        disc = tags.disc,
        discTotal = tags.discTotal,
        duration = metadata?.audioProperties?.duration,
        year = tags.year,
        genres = tags.genres,
        replayGainTrack = tags.replayGainTrack,
        replayGainAlbum = tags.replayGainAlbum,
        lyrics = tags.lyrics,
        grouping = tags.grouping,
        bitRate = metadata?.audioProperties?.bitrate,
        bitDepth = taglibBitDepth(metadata?.audioProperties?.codec, metadata?.audioProperties?.bitsPerSample),
        sampleRate = metadata?.audioProperties?.sampleRate,
        channelCount = metadata?.audioProperties?.channelCount,
        albumArtists = tags.albumArtists,
        artistsTag = tags.artistsTag,
        artistDisplay = tags.artistDisplay,
        compilation = tags.compilation,
        mbTrackId = tags.mbTrackId,
        mbAlbumId = tags.mbAlbumId,
        mbReleaseGroupId = tags.mbReleaseGroupId,
        mbArtistIds = tags.mbArtistIds,
        mbAlbumArtistIds = tags.mbAlbumArtistIds
    )
}
