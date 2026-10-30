package com.simplecityapps.shuttle.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.ui.promo.PodcastsPromo
import com.simplecityapps.shuttle.ui.promo.PromoSurface
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Whether Home shows the Shuttle Podcasts card, decided once when Home opens and cleared by a tap or dismiss. */
@ViewModelKey(PodcastsPromoViewModel::class)
@ContributesIntoMap(AppScope::class)
class PodcastsPromoViewModel @Inject constructor(
    private val promo: PodcastsPromo
) : ViewModel() {
    private val _uiState = MutableStateFlow(false)
    val uiState: StateFlow<Boolean> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (promo.isDue()) {
                _uiState.value = true
                promo.onShown()
            }
        }
    }

    fun onGetIt() {
        _uiState.value = false
        promo.onTapped(PromoSurface.Card)
    }

    fun onDismiss() {
        _uiState.value = false
        promo.onDismissed()
    }
}
