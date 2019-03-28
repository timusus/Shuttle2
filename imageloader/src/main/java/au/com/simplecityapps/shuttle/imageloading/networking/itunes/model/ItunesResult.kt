package au.com.simplecityapps.shuttle.imageloading.networking.itunes.model

import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import com.google.gson.annotations.SerializedName
import java.util.*

class ItunesResult : ArtworkUrlResult {

    private val results = ArrayList<Result>()

    override val artworkUrl: String?
        get() = if (results.isEmpty()) null else results[0].url


    class Result {
        @SerializedName("artworkUrl100")
        var url: String? = null
    }
}