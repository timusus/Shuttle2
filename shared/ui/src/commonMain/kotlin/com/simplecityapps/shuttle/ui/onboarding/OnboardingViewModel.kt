package com.simplecityapps.shuttle.ui.onboarding

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.inject.hilt.HiltViewModel
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.model.defaultMediaProvider
import com.simplecityapps.shuttle.preferences.GeneralPreferenceManager
import com.simplecityapps.shuttle.savedstate.SavedStateHandle
import com.simplecityapps.shuttle.ui.ViewModel
import com.simplecityapps.shuttle.ui.domain.model.mediaprovider.GetMediaProviderTypes
import com.simplecityapps.shuttle.ui.domain.model.mediaprovider.SetMediaProviders
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getMediaProviderTypes: GetMediaProviderTypes,
    val setMediaProviders: SetMediaProviders,
    val preferenceManager: GeneralPreferenceManager
) : ViewModel() {

    private val isOnboarding = savedStateHandle.get<Boolean>(ARG_ONBOARDING) ?: false

    val selectedMediaProviders = getMediaProviderTypes(if (isOnboarding) listOfNotNull(defaultMediaProvider()) else emptyList())
        .onEach { println("Media providers: $it, isOnboarding: $isOnboarding") }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = emptyList()
        )

    fun addMediaProvider(mediaProviderType: MediaProviderType) {
        coroutineScope.launch {
            setMediaProviders((selectedMediaProviders.value + mediaProviderType).distinct())
        }
    }

    fun setOnboardingComplete() {
        coroutineScope.launch {
            preferenceManager.setHasOnboarded(true)
        }
    }

    companion object {
        const val ARG_ONBOARDING = "isOnboarding"
    }
}