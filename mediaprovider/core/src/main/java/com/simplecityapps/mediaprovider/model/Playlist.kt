package com.simplecityapps.mediaprovider.model

import java.io.Serializable

class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val duration: Int
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Playlist

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}