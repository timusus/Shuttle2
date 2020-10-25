package com.simplecityapps.mediaprovider.model

import androidx.annotation.Keep
import java.io.Serializable
import java.util.*

@Keep
data class Song(
    val id: Long,
    val name: String,
    val albumArtist: String,
    val artist: String,
    val album: String,
    val track: Int,
    val disc: Int,
    val duration: Int,
    val year: Int,
    val genres: List<String>,
    val path: String,
    val size: Long,
    val mimeType: String,
    val lastModified: Date,
    val lastPlayed: Date?,
    val lastCompleted: Date?,
    val playCount: Int,
    var playbackPosition: Int,
    val blacklisted: Boolean,
    var mediaStoreId: Long? = null
) : Serializable {

    val type: Type
        get() {
            return when {
                path.contains("audiobook", true) || path.endsWith("m4b", true) -> Type.Audiobook
                path.contains("podcast", true) -> Type.Podcast
                else -> Type.Audio
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Song) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "Song(" +
                "name='$name'," +
                " albumArtist='$albumArtist'," +
                " artist='$artist'," +
                " album='$album'," +
                " track=$track," +
                " disc=$disc," +
                " duration=$duration," +
                " year=$year," +
                " genres=$genres," +
                " path='$path'," +
                " size=$size," +
                " mimeType='$mimeType'," +
                " lastModified=$lastModified," +
                " lastPlayed=$lastPlayed," +
                " lastCompleted=$lastCompleted," +
                " playCount=$playCount," +
                " playbackPosition=$playbackPosition," +
                " blacklisted=$blacklisted" +
                ")"
    }


    enum class Type {
        Audio, Audiobook, Podcast
    }
}