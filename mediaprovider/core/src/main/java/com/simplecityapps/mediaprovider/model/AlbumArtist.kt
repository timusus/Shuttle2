package com.simplecityapps.mediaprovider.model

import java.io.Serializable

class AlbumArtist(
    var id: Long,
    val name: String,
    val albumCount: Long,
    val songCount: Long
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AlbumArtist) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "id=$id, " +
                "\nname='$name', " +
                "\nalbumCount=$albumCount, " +
                "\nsongCount=$songCount"
    }
}