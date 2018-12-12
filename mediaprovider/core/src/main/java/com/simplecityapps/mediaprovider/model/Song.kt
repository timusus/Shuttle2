package com.simplecityapps.mediaprovider.model

import java.io.Serializable

data class Song(
    val id: Long,
    val name: String,
    val albumArtistId: Long,
    val albumArtistName: String,
    val albumId: Long,
    val albumName: String,
    val track: Int,
    val trackTotal: Int,
    val disc: Int,
    val discTotal: Int,
    val duration: Long,
    val year: Int,
    val path: String
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Song) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}