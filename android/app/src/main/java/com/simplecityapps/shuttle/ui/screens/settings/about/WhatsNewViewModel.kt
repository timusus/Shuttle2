package com.simplecityapps.shuttle.ui.screens.settings.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.BuildConfig
import com.simplecityapps.shuttle.persistence.GeneralPreferenceManager
import com.simplecityapps.shuttle.ui.screens.changelog.Changeset
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** The release notes bundled with the app, newest first. */
class ChangelogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {
    suspend fun changelog(): List<Changeset> = withContext(Dispatchers.IO) {
        try {
            val type = Types.newParameterizedType(MutableList::class.java, Changeset::class.java)
            moshi.adapter<List<Changeset>>(type).lenient().fromJson(context.assets.open("changelog.json").bufferedReader().use { it.readText() })
        } catch (e: RuntimeException) {
            Timber.e(e, "Invalid changelog")
            null
        }.orEmpty()
    }
}

data class WhatsNewUiState(
    val changesets: List<Changeset> = emptyList(),
    val loading: Boolean = true
)

@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    changelogRepository: ChangelogRepository,
    generalPreferenceManager: GeneralPreferenceManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(WhatsNewUiState())
    val uiState: StateFlow<WhatsNewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = WhatsNewUiState(changesets = changelogRepository.changelog(), loading = false)
        }
        generalPreferenceManager.lastViewedChangelogVersion = BuildConfig.VERSION_NAME
    }
}
