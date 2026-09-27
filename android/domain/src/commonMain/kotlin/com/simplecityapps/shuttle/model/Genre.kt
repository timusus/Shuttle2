package com.simplecityapps.shuttle.model

data class Genre(
    val name: String,
    val songCount: Int,
    val duration: Int,
    val mediaProviders: List<MediaProviderType>
)
