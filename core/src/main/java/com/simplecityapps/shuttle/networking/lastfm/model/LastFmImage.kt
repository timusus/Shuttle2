package com.simplecityapps.shuttle.networking.lastfm.model

import com.google.gson.annotations.SerializedName

class LastFmImage {

    @SerializedName("#text")
    var url: String? = null

    @SerializedName("size")
    var size: String? = null
}