package com.simplecityapps.shuttle.networking.lastfm

import com.simplecityapps.shuttle.networking.lastfm.model.LastFmAlbum
import com.simplecityapps.shuttle.networking.lastfm.model.LastFmArtist
import com.simplecityapps.shuttle.networking.lastfm.model.LastFmTrack
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class LastFmService(okHttpClient: OkHttpClient) {

    interface LastFm {
        @GET("?api_key=$API_KEY&format=json&autocorrect=1&method=track.getInfo")
        fun getLastFmTrack(
            @Query("artist") artist: String,
            @Query("track") track: String
        ): Call<LastFmTrack>

        @GET("?api_key=$API_KEY&format=json&autocorrect=1&method=album.getInfo")
        fun getLastFmAlbum(
            @Query("artist") artist: String,
            @Query("album") album: String
        ): Call<LastFmAlbum>

        @GET("?api_key=$API_KEY&format=json&autocorrect=1&method=artist.getInfo")
        fun getLastFmArtist(
            @Query("artist") artist: String
        ): Call<LastFmArtist>
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val lastFm = retrofit.create(LastFm::class.java)

    companion object {
        private val BASE_URL = "https://ws.audioscrobbler.com/2.0/"
        const val API_KEY = "206993ea109315882749d5bc7b2e704d"
    }
}

