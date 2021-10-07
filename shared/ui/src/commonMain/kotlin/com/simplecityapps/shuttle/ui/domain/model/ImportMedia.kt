package com.simplecityapps.shuttle.domain.model

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.preferences.GeneralPreferenceManager

data class ImportMedia @Inject constructor(
    private val preferenceManager: GeneralPreferenceManager
) {

}