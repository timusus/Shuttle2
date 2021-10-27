package com.simplecityapps.shuttle.ui.domain.model.prefs

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.preferences.GeneralPreferenceManager
import kotlinx.coroutines.flow.Flow

data class GetHasOnboarded @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager
) {
    operator fun invoke(): Flow<Boolean> = preferenceManager.getHasOnboarded()
}