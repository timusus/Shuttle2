package au.com.simplecityapps.shuttle.imageloading.networking.lastfm.model

import com.google.gson.annotations.SerializedName

class LastFmImage {

    @SerializedName("#text")
    var url: String? = null

    @SerializedName("size")
    var size: String? = null
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