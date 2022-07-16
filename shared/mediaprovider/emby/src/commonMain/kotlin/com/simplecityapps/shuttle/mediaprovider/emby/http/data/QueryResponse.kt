package com.simplecityapps.shuttle.mediaprovider.emby.http.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueryResponse(
    @SerialName("Items") val items: List<ItemResponse>,
    @SerialName("TotalRecordCount") val totalRecordCount: Int
)