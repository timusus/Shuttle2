package com.simplecityapps.mediaprovider.model

import android.os.Parcelable
import androidx.annotation.Keep
import com.simplecityapps.mediaprovider.repository.SongQuery
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class SmartPlaylist(val nameResId: Int, val songQuery: SongQuery) : Parcelable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SmartPlaylist

        if (nameResId != other.nameResId) return false

        return true
    }

    override fun hashCode(): Int {
        return nameResId
    }
}
