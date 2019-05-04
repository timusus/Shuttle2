package com.simplecityapps.mediaprovider.model

import java.io.Serializable

class Album(
    val id: Long,
    val name: String,
    val albumArtistId: Long,
    val albumArtistName: String,
    val songCount: Int,
    val duration: Int,
    val year: Int
) : Serializable {

    var sortKey: String? = Regex.articlePattern.matcher(name).replaceAll("")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Album) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "id=$id," +
                "\nname='$name'," +
                "\nalbumArtistId=$albumArtistId," +
                "\nalbumArtistName='$albumArtistName'," +
                "\nsongCount=$songCount," +
                "\nduration=$duration," +
                "\nyear=$year"
    }
}