package au.com.simplecityapps.shuttle.imageloading.glide.provider.remote

import au.com.simplecityapps.shuttle.imageloading.glide.provider.ArtworkProvider
import au.com.simplecityapps.shuttle.imageloading.networking.ArtworkUrlResult
import retrofit2.Call

interface RemoteArtworkProvider : ArtworkProvider {

    fun getArtworkUri(): Call<out ArtworkUrlResult>
}