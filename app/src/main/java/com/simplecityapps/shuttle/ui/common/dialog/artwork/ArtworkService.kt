package com.simplecityapps.shuttle.ui.common.dialog.artwork

import com.simplecityapps.networking.retrofit.NetworkResult
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class ImageList(val images: List<Image>)

@JsonClass(generateAdapter = true)
data class Image(val provider: String, val url: String)

interface ArtworkService {

    @GET("/v1/artwork/all")
    suspend fun getArtistImages(
        @Query("artist") artist: String
    ): NetworkResult<ImageList>

    @GET("/v1/artwork/all")
    suspend fun getAlbumImages(
        @Query("artist") artist: String,
        @Query("album") album: String
    ): NetworkResult<ImageList>

}