package com.simplecityapps.trial

import com.simplecityapps.networking.retrofit.NetworkResult
import retrofit2.http.GET
import retrofit2.http.Query

interface PromoCodeService {

    @GET("v1/promo_code")
    suspend fun getPromoCode(@Query("email") emailAddress: String): NetworkResult<PromoCode>
}