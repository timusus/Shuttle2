package com.simplecityapps.mediaprovider

import androidx.annotation.DrawableRes
import com.simplecityapps.mediaprovider.model.Song

interface MediaProvider {

    enum class Type {
        Shuttle, MediaStore, Emby;

        companion object {
            fun init(ordinal: Int): Type {
                return when (ordinal) {
                    Shuttle.ordinal -> Shuttle
                    MediaStore.ordinal -> MediaStore
                    Emby.ordinal -> Emby
                    else -> Shuttle
                }
            }
        }

        fun title(): String {
            return when (this) {
                Shuttle -> "Shuttle"
                MediaStore -> "Android Media Store"
                Emby -> "Emby"
            }
        }

        fun description(): String {
            return when (this) {
                Shuttle -> "Scans selected folders & files"
                MediaStore -> "Android-managed database"
                Emby -> "Personal media server"
            }
        }

        @DrawableRes
        fun iconResId(): Int {
            return when (this) {
                Shuttle -> R.drawable.ic_launcher_foreground_blue
                MediaStore -> R.drawable.ic_baseline_android_24
                Emby -> R.drawable.ic_emby
            }
        }
    }

    val type: Type

    suspend fun findSongs(callback: ((song: Song, progress: Int, total: Int) -> (Unit))? = null): List<Song>?
}