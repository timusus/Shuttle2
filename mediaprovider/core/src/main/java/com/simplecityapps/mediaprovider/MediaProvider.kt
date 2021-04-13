package com.simplecityapps.mediaprovider

import android.os.Parcelable
import androidx.annotation.DrawableRes
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.parcelize.Parcelize

interface MediaProvider {

    @Parcelize
    enum class Type(val isRemote: Boolean) : Parcelable {
        Shuttle(false),
        MediaStore(false),
        Emby(true),
        Jellyfin(true),
        Plex(true);

        companion object {
            fun init(ordinal: Int): Type {
                return when (ordinal) {
                    Shuttle.ordinal -> Shuttle
                    MediaStore.ordinal -> MediaStore
                    Emby.ordinal -> Emby
                    Jellyfin.ordinal -> Jellyfin
                    Plex.ordinal -> Plex
                    else -> Shuttle
                }
            }
        }

        fun title(): String {
            return when (this) {
                Shuttle -> "Shuttle"
                MediaStore -> "Android Media Store"
                Jellyfin -> "Jellyfin"
                Emby -> "Emby"
                Plex -> "Plex"
            }
        }

        fun description(): String {
            return when (this) {
                Shuttle -> "Scans selected folders & files"
                MediaStore -> "Android-managed database"
                Jellyfin -> "Personal media server"
                Emby -> "Personal media server"
                Plex -> "Personal media server"
            }
        }

        @DrawableRes
        fun iconResId(): Int {
            return when (this) {
                Shuttle -> R.drawable.ic_launcher_foreground_blue
                MediaStore -> R.drawable.ic_baseline_android_24
                Jellyfin -> R.drawable.ic_jellyfin
                Emby -> R.drawable.ic_emby
                Plex -> R.drawable.ic_plex
            }
        }
    }

    val type: Type

    suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> (Unit))? = null): List<Song>?
}