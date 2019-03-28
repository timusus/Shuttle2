package au.com.simplecityapps.shuttle.imageloading.networking.itunes

import au.com.simplecityapps.shuttle.imageloading.networking.itunes.model.ItunesResult
import com.simplecityapps.mediaprovider.model.Album
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class ItunesService(okHttpClient: OkHttpClient, gsonConverterFactory: GsonConverterFactory) {

    interface Itunes {

        @GET("?entity=album&limit=1")
        fun getItunesAlbum(
            @Query("term") string: String
        ): Call<ItunesResult>
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(gsonConverterFactory)
        .build()

    val itunes: ItunesService.Itunes = retrofit.create(ItunesService.Itunes::class.java)


    companion object {
        private const val BASE_URL = "https://itunes.apple.com/search/"
    }
}

fun ItunesService.Itunes.getItunesAlbum(album: Album): Call<ItunesResult> {
    return getItunesAlbum("${album.albumArtistName} ${album.name}")
}