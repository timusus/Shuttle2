package au.com.simplecityapps.shuttle.imageloading.glide.provider

import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import retrofit2.Call

interface ArtworkProvider {

    fun getCacheKey(): String

    fun getArtworkUri(): Call<out ArtworkUrlResult>
}