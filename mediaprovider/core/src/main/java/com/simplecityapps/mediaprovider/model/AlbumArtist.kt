package com.simplecityapps.mediaprovider.model

import androidx.annotation.Keep
import java.io.Serializable

@Keep
class AlbumArtist(
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val playCount: Int
) : Serializable {

    var sortKey: String? = Regex.articlePattern.matcher(name).replaceAll("")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlbumArtist

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return "AlbumArtist(name='$name', albumCount=$albumCount, songCount=$songCount, playCount=$playCount, sortKey=$sortKey)"
    }
}