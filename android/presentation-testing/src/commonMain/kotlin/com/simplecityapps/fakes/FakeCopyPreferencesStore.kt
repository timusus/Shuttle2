package com.simplecityapps.fakes

import com.simplecityapps.shuttle.model.SongCopyPreferences
import com.simplecityapps.shuttle.ui.actions.CopyPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCopyPreferencesStore(merging: Boolean = true) : CopyPreferencesStore {
    override val mergeCopies = MutableStateFlow(merging)
    override val preferences = MutableStateFlow(SongCopyPreferences())

    override suspend fun update(change: (SongCopyPreferences) -> SongCopyPreferences) {
        preferences.value = change(preferences.value)
    }
}
