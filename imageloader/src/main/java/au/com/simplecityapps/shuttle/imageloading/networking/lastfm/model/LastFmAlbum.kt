package au.com.simplecityapps.shuttle.imageloading.networking.lastfm.model

import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import com.google.gson.annotations.SerializedName

class LastFmAlbum: ArtworkUrlResult {

    @SerializedName("album")
    var album: Album? = null

    override val artworkUrl
        get() = album?.images?.getBestImageUrl()

    class Album {
        @SerializedName("name")
        var name: String? = null

        @SerializedName("image")
        var images: List<LastFmImage> = ArrayList()
    }
}