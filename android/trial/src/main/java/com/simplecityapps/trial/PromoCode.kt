package com.simplecityapps.trial

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PromoCode(
    val promoCode: String
)
