package com.simplecityapps.shuttle.mediaprovider.jellyfin.http.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueryResult(
    @SerialName("Items") val items: List<Item>,
    @SerialName("TotalRecordCount") val totalRecordCount: Int,
    @SerialName("StartIndex") val startIndex: Int
)