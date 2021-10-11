package com.simplecityapps.shuttle.compose.ui.components.mediaprovider

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.simplecityapps.shuttle.compose.R
import com.simplecityapps.shuttle.model.MediaProviderType

val MediaProviderType.titleResId: Int
    @StringRes
    get() {
        return when (this) {
            MediaProviderType.Shuttle -> R.string.media_provider_title_s2
            MediaProviderType.MediaStore -> R.string.media_provider_title_media_store
            MediaProviderType.Jellyfin -> R.string.media_provider_title_jellyfin
            MediaProviderType.Emby -> R.string.media_provider_title_emby
            MediaProviderType.Plex -> R.string.media_provider_title_plex
        }
    }

val MediaProviderType.descriptionResId: Int
    @StringRes
    get() {
        return when (this) {
            MediaProviderType.Shuttle -> R.string.media_provider_description_s2
            MediaProviderType.MediaStore -> R.string.media_provider_description_media_store
            MediaProviderType.Jellyfin -> R.string.media_provider_description_jellyfin
            MediaProviderType.Emby -> R.string.media_provider_description_emby
            MediaProviderType.Plex -> R.string.media_provider_description_plex
        }
    }

val MediaProviderType.iconResId: Int
    @DrawableRes
    get() {
        return when (this) {
            MediaProviderType.Shuttle -> R.drawable.ic_s2
            MediaProviderType.MediaStore -> R.drawable.ic_baseline_android_24
            MediaProviderType.Jellyfin -> R.drawable.ic_jellyfin
            MediaProviderType.Emby -> R.drawable.ic_emby
            MediaProviderType.Plex -> R.drawable.ic_plex
        }
    }