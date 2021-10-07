package com.simplecityapps.shuttle.ui.root

import com.simplecityapps.shuttle.ui.ViewModel
import com.simplecityapps.shuttle.domain.model.GetHasOnboarded
import com.simplecityapps.shuttle.inject.Inject
import com.simplecityapps.shuttle.inject.hilt.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RootViewModel @Inject constructor(
    getHasOnboarded: GetHasOnboarded
) : ViewModel() {

    val hasOnboarded = getHasOnboarded().stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = false
    )
}