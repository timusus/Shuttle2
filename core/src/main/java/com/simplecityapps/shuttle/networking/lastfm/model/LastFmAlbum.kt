package com.simplecityapps.shuttle.networking.lastfm.model

import com.google.gson.annotations.SerializedName

class LastFmAlbum {

    @SerializedName("album")
    var album: Album? = null

    val imageUrl
        get() = album?.images?.getBestImageUrl()

    class Album {
        @SerializedName("name")
        var name: String? = null

        @SerializedName("image")
        var images: List<LastFmImage> = ArrayList()
    }
}