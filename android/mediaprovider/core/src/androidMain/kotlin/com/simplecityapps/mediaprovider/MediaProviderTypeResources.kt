package com.simplecityapps.mediaprovider

import android.content.Context
import androidx.annotation.DrawableRes
import com.simplecityapps.shuttle.model.MediaProviderType

fun MediaProviderType.title(context: Context): String = when (this) {
    MediaProviderType.Shuttle -> context.getString(R.string.media_provider_title_s2)
    MediaProviderType.MediaStore -> context.getString(R.string.media_provider_title_media_store)
    MediaProviderType.Jellyfin -> context.getString(R.string.media_provider_title_jellyfin)
    MediaProviderType.Emby -> context.getString(R.string.media_provider_title_emby)
    MediaProviderType.Plex -> context.getString(R.string.media_provider_title_plex)
    MediaProviderType.Subsonic -> context.getString(R.string.media_provider_title_subsonic)
}

fun MediaProviderType.description(context: Context): String = when (this) {
    MediaProviderType.Shuttle -> context.getString(R.string.media_provider_description_s2)
    MediaProviderType.MediaStore -> context.getString(R.string.media_provider_description_media_store)
    MediaProviderType.Jellyfin -> context.getString(R.string.media_provider_description_jellyfin)
    MediaProviderType.Emby -> context.getString(R.string.media_provider_description_emby)
    MediaProviderType.Plex -> context.getString(R.string.media_provider_description_plex)
    MediaProviderType.Subsonic -> context.getString(R.string.media_provider_description_subsonic)
}

@DrawableRes
fun MediaProviderType.iconResId(): Int = when (this) {
    MediaProviderType.Shuttle -> R.drawable.ic_s2
    MediaProviderType.MediaStore -> R.drawable.ic_baseline_android_24
    MediaProviderType.Jellyfin -> R.drawable.ic_jellyfin
    MediaProviderType.Emby -> R.drawable.ic_emby
    MediaProviderType.Plex -> R.drawable.ic_plex
    MediaProviderType.Subsonic -> R.drawable.ic_subsonic
}
