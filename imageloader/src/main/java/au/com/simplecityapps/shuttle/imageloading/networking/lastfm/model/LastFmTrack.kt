package au.com.simplecityapps.shuttle.imageloading.networking.lastfm.model

import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import com.google.gson.annotations.SerializedName

class LastFmTrack: ArtworkUrlResult {

    @SerializedName("track")
    var track: Track? = null

    override val artworkUrl
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