package com.simplecityapps.trial

import com.simplecityapps.networking.networkResult
import com.simplecityapps.networking.retrofit.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

const val S2_API_BASE_URL = "https://api.shuttlemusicplayer.app/"

class PromoCodeService(private val client: HttpClient) {
    suspend fun getPromoCode(emailAddress: String): NetworkResult<PromoCode> = client.networkResult {
        get("${S2_API_BASE_URL}v1/promo_code") {
            parameter("email", emailAddress)
        }
    }
}
