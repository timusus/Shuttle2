package com.simplecityapps.mediaprovider.server.mediabrowser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    @SerialName("Id") val id: String,
    @SerialName("Policy") val policy: Policy? = null
)

@Serializable
data class Policy(
    @SerialName("EnableContentDownloading") val enableContentDownloading: Boolean = false
)
