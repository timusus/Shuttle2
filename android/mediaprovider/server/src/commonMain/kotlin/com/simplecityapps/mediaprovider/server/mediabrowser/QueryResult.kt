package com.simplecityapps.mediaprovider.server.mediabrowser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueryResult(
    @SerialName("Items") val items: List<Item> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0
)
