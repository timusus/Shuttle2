package au.com.simplecityapps.shuttle.imageloading.networking.lastfm.model

import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import com.google.gson.annotations.SerializedName

class LastFmArtist: ArtworkUrlResult {

    @SerializedName("artist")
    var artist: Artist? = null

    override val artworkUrl
        get() = artist?.images?.getBestImageUrl()

    class Artist {
        @SerializedName("name")
        var name: String? = null

        @SerializedName("image")
        var images: List<LastFmImage>? = ArrayList()
    }
}