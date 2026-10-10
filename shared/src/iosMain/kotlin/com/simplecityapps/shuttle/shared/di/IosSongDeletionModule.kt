package com.simplecityapps.shuttle.shared.di

import com.simplecityapps.shuttle.ui.actions.MediaStoreSongDeleter
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/** iOS has no MediaStore, so nothing is ever deleted through [MediaStoreSongDeleter]. */
@ContributesTo(AppScope::class)
@BindingContainer
object IosSongDeletionModule {
    @Provides
    fun provideMediaStoreSongDeleter(): MediaStoreSongDeleter = MediaStoreSongDeleter { _, _ -> emptySet() }
}
