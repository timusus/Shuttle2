package com.simplecityapps.mediaprovider.model

import java.io.Serializable
import java.util.*

class Song(
    val id: Long,
    val name: String,
    val albumArtistId: Long,
    val albumArtistName: String,
    val albumId: Long,
    val albumName: String,
    val track: Int,
    val disc: Int,
    val duration: Long,
    val year: Int,
    val path: String,
    val size: Long,
    val lastModified: Date
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

    override fun toString(): String {
        return "id=$id," +
                "\nname='$name'," +
                "\nalbumArtistId=$albumArtistId," +
                "\nalbumArtistName='$albumArtistName'," +
                "\nalbumId=$albumId," +
                "\nalbumName='$albumName'," +
                "\ntrack=$track," +
                "\ndisc=$disc," +
                "\nduration=$duration," +
                "\nyear=$year," +
                "\npath='$path'," +
                "\nsize=$size," +
                "\nlastModified=$lastModified"
    }
}