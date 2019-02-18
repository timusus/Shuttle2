package com.simplecityapps.shuttle.networking.lastfm.model

import com.google.gson.annotations.SerializedName

class LastFmTrack {

    @SerializedName("track")
    var track: Track? = null

    val imageUrl
        get() = track?.album?.images?.getBestImageUrl()

    class Track {

        @SerializedName("album")
        var album: TrackAlbum? = null

        class TrackAlbum {

            @SerializedName("album")
            var album: TrackAlbum? = null

            @SerializedName("image")
            var images: List<LastFmImage> = ArrayList()
        }
    }
}

fun List<LastFmImage>.getBestImageUrl(): String? {
    return arrayOf("mega", "extralarge", "large", "medium")
        .mapNotNull { size -> findBySize(size)?.url }
        .firstOrNull()
        ?.replaceFirst(Regex("/\\d*s(/|$)|/\\d*x\\d*(/|$)"), "/1080s/")
}

private fun List<LastFmImage>.findBySize(size: String): LastFmImage? {
    return this.firstOrNull { image -> image.size.equals(size) }
}