package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType

data class SongImportResult(
    val mediaProviderType: MediaProviderType,
    val inserts: Int,
    val updates: Int,
    val deletes: Int
)

data class PlaylistImportResult(
    val mediaProviderType: MediaProviderType
)
