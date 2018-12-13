package com.simplecityapps.mediaprovider.model

import java.io.Serializable

data class Album(
    val id: Long,
    val name: String,
    val albumArtistId: Long,
    val albumArtistName: String
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Album) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}