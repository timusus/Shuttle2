package com.simplecityapps.shuttle.ui.onboarding

import com.simplecityapps.shuttle.ui.ViewModel
import com.simplecityapps.shuttle.domain.model.GetMediaProviders
import com.simplecityapps.shuttle.domain.model.SetMediaProviders
import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.inject.hilt.HiltViewModel
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.savedstate.SavedStateHandle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getMediaProviders: GetMediaProviders,
    val setMediaProviders: SetMediaProviders
) : ViewModel() {

    private val isOnboarding = savedStateHandle.get<Boolean>(ARG_ONBOARDING) ?: false

    val selectedMediaProviders = getMediaProviders(if (isOnboarding) listOf(MediaProviderType.MediaStore) else emptyList())
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

    companion object {
        const val ARG_ONBOARDING = "isOnboarding"
    }
}