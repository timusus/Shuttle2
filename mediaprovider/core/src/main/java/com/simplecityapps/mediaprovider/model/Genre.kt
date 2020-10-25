package com.simplecityapps.mediaprovider.model

import androidx.annotation.Keep
import java.io.Serializable

@Keep
data class Genre(
    val name: String,
    val songCount: Int,
    val duration: Int
) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Genre

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }
}