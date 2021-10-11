package com.simplecityapps.shuttle.ui.mediaprovider

import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.inject.hilt.HiltViewModel
import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.savedstate.SavedStateHandle
import com.simplecityapps.shuttle.ui.ViewModel
import com.simplecityapps.shuttle.ui.domain.model.GetMediaProviderTypes
import com.simplecityapps.shuttle.ui.domain.model.SetMediaProviders
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MediaProviderSelectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getMediaProviderTypes: GetMediaProviderTypes,
    val setMediaProviders: SetMediaProviders
) : ViewModel() {

    private val isOnboarding = savedStateHandle.get<Boolean>(ARG_ONBOARDING) ?: false

    val selectedMediaProviders = getMediaProviderTypes(if (isOnboarding) listOf(MediaProviderType.MediaStore) else emptyList())
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = emptyList()
        )

    fun removeMediaProvider(mediaProviderType: MediaProviderType) {
        coroutineScope.launch {
            setMediaProviders((selectedMediaProviders.value - mediaProviderType).distinct())
        }
    }

    companion object {
        const val ARG_ONBOARDING = "isOnboarding"
    }
}