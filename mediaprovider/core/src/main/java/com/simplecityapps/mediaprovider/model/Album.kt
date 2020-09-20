package com.simplecityapps.mediaprovider.model

import androidx.annotation.Keep
import java.io.Serializable

@Keep
class Album(
    val name: String,
    val albumArtist: String,
    val songCount: Int,
    val duration: Int,
    val year: Int,
    val playCount: Int
) : Serializable {

    var sortKey: String? = name.removeArticles()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Album

        if (name != other.name) return false
        if (albumArtist != other.albumArtist) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + albumArtist.hashCode()
        return result
    }

    override fun toString(): String {
        return "Album(name='$name', albumArtist='$albumArtist', songCount=$songCount, duration=$duration, year=$year, playCount=$playCount, sortKey=$sortKey)"
    }
}