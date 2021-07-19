package com.simplecityapps.mediaprovider

import android.content.Context
import android.os.Parcelable
import androidx.annotation.DrawableRes
import com.simplecityapps.mediaprovider.model.Playlist
import com.simplecityapps.mediaprovider.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize

interface MediaProvider {

    @Parcelize
    enum class Type(val isRemote: Boolean, val supportsTagEditing: Boolean) : Parcelable {
        Shuttle(isRemote = false, supportsTagEditing = true),
        MediaStore(isRemote = false, supportsTagEditing = false),
        Emby(isRemote = true, supportsTagEditing = false),
        Jellyfin(isRemote = true, supportsTagEditing = false),
        Plex(isRemote = true, supportsTagEditing = false);

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

        fun title(context: Context): String {
            return when (this) {
                Shuttle -> context.getString(R.string.media_provider_title_s2)
                MediaStore -> context.getString(R.string.media_provider_title_media_store)
                Jellyfin -> context.getString(R.string.media_provider_title_jellyfin)
                Emby -> context.getString(R.string.media_provider_title_emby)
                Plex -> context.getString(R.string.media_provider_title_plex)
            }
        }

        fun description(context: Context): String {
            return when (this) {
                Shuttle -> context.getString(R.string.media_provider_description_s2)
                MediaStore -> context.getString(R.string.media_provider_description_media_store)
                Jellyfin -> context.getString(R.string.media_provider_description_jellyfin)
                Emby -> context.getString(R.string.media_provider_description_emby)
                Plex -> context.getString(R.string.media_provider_description_plex)
            }
        }

        @DrawableRes
        fun iconResId(): Int {
            return when (this) {
                Shuttle -> R.drawable.ic_s2
                MediaStore -> R.drawable.ic_baseline_android_24
                Jellyfin -> R.drawable.ic_jellyfin
                Emby -> R.drawable.ic_emby
                Plex -> R.drawable.ic_plex
            }
        }
    }

    val type: Type

    fun findSongs(): Flow<FlowEvent<List<Song>, MessageProgress>>

    fun findPlaylists(existingPlaylists: List<Playlist>, existingSongs: List<Song>): Flow<FlowEvent<List<MediaImporter.PlaylistUpdateData>, MessageProgress>>
}