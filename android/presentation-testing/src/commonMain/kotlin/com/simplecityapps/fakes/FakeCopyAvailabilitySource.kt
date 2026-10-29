package com.simplecityapps.fakes

import com.simplecityapps.shuttle.model.SongCopyAvailability
import com.simplecityapps.shuttle.ui.actions.CopyAvailabilitySource
import com.simplecityapps.shuttle.ui.actions.CopyPreferencesStore
import com.simplecityapps.shuttle.ui.actions.ObserveCopyContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCopyAvailabilitySource(initial: SongCopyAvailability = SongCopyAvailability()) : CopyAvailabilitySource {
    val availability = MutableStateFlow(initial)

    override fun observe(): Flow<SongCopyAvailability> = availability
}

fun fakeObserveCopyContext(
    store: CopyPreferencesStore = FakeCopyPreferencesStore(),
    availability: CopyAvailabilitySource = FakeCopyAvailabilitySource(),
) = ObserveCopyContext(store, availability)
