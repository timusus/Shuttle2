package com.simplecityapps.mediaprovider.settings

import com.simplecityapps.shuttle.model.SongCopyPreferences
import com.simplecityapps.shuttle.ui.actions.CopyPreferencesStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@ContributesBinding(AppScope::class)
class LibraryCopyPreferencesStore @Inject constructor(
    private val settings: LibrarySettings,
) : CopyPreferencesStore {
    override val mergeCopies: Flow<Boolean> = settings.mergeCopies.flow

    override val preferences: Flow<SongCopyPreferences> =
        combine(settings.primaryCopies.flow, settings.primaryAlbums.flow, settings.preferredCopy.flow, ::SongCopyPreferences)

    // Each set is its own setting and a Preference has no batch write, so the lock is what keeps updates whole.
    private val lock = Mutex()

    override suspend fun update(change: (SongCopyPreferences) -> SongCopyPreferences) = lock.withLock {
        val before = SongCopyPreferences(settings.primaryCopies.value, settings.primaryAlbums.value, settings.preferredCopy.value)
        val after = change(before)
        if (after.primaryCopies != before.primaryCopies) settings.primaryCopies.value = after.primaryCopies
        if (after.primaryAlbums != before.primaryAlbums) settings.primaryAlbums.value = after.primaryAlbums
    }
}
