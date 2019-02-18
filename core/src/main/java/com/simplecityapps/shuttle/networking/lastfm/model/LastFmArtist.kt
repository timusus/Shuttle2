package com.simplecityapps.shuttle.networking.lastfm.model

import com.google.gson.annotations.SerializedName

class LastFmArtist {

    @SerializedName("artist")
    var artist: Artist? = null

    val imageUrl
        get() = artist?.images?.getBestImageUrl()

    class Artist {
        @SerializedName("name")
        var name: String? = null

        @SerializedName("image")
        var images: List<LastFmImage>? = ArrayList()
    }
}