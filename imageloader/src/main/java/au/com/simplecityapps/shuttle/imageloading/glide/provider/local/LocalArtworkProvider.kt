package au.com.simplecityapps.shuttle.imageloading.glide.provider.local

import au.com.simplecityapps.shuttle.imageloading.glide.provider.ArtworkProvider

interface LocalArtworkProvider : ArtworkProvider {

    fun getPath(): String

}