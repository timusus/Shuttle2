package com.simplecityapps.mediaprovider

import com.simplecityapps.shuttle.model.MediaProviderType
import dev.zacsweers.metro.MapKey

/**
 * Keys a remote provider's per-provider binding (its MediaInfoProvider, its Quick Connect) by the provider it's for.
 * Each provider module contributes its own entry (`@IntoMap`), so a module that needs every provider's (playback)
 * asks for the map rather than depending on the provider modules.
 */
@MapKey
annotation class MediaProviderTypeKey(val value: MediaProviderType)
