package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simplecityapps.shuttle.ui.common.PendingEvent
import com.simplecityapps.shuttle.ui.common.PendingEvents
import com.simplecityapps.trial.Billing
import com.simplecityapps.trial.Entitlement
import com.simplecityapps.trial.MonetisationAnalytics
import com.simplecityapps.trial.PaywallOffer
import com.simplecityapps.trial.PaywallOffers
import com.simplecityapps.trial.PaywallPlan
import com.simplecityapps.trial.PaywallSource
import com.simplecityapps.trial.ProSource
import com.simplecityapps.trial.RestoreResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the user stands, as the paywall shows it. */
sealed interface PaywallStatus {
    /** Play hasn't answered yet, so it isn't known whether the user already owns Pro. */
    data object Checking : PaywallStatus

    /** Free, and the server trial hasn't started: the first song played from a server starts it. */
    data object TrialAvailable : PaywallStatus

    /** Free, after the trial. */
    data object TrialEnded : PaywallStatus

    data class Trial(val daysLeft: Int) : PaywallStatus

    data class Pro(val source: ProSource) : PaywallStatus
}

data class PaywallUiState(
    val status: PaywallStatus = PaywallStatus.TrialAvailable,
    val offers: PaywallOffers = PaywallOffers.Loading,
    val selectedPlan: PaywallPlan = PaywallPlan.Lifetime,
    val restoring: Boolean = false,
    val events: List<PendingEvent<PaywallUiEvent>> = emptyList()
) {
    /** The offer for each plan Play sells, in plan order; empty while loading or unavailable. */
    val available: List<PaywallOffer> get() = (offers as? PaywallOffers.Available)?.offers.orEmpty()

    /** The offer the purchase button buys, or null if there's nothing to buy yet. */
    val selectedOffer: PaywallOffer? get() = available.firstOrNull { it.plan == selectedPlan } ?: available.firstOrNull()

    /** The trial comes first for a user who hasn't had it; everyone else is offered Pro. */
    val primaryAction: PaywallPrimaryAction
        get() = if (status == PaywallStatus.TrialAvailable) PaywallPrimaryAction.StartTrial else PaywallPrimaryAction.Purchase

    /** Before and during the trial, the paywall says what stops once it ends, so the trial holds no surprise. */
    val explainsTrialEnd: Boolean get() = status == PaywallStatus.TrialAvailable || status is PaywallStatus.Trial
}

/** What the paywall's main button does. */
enum class PaywallPrimaryAction {
    /** Opens Sources to add a server, whose first song played starts the trial. Buying moves to a second button. */
    StartTrial,

    /** Buys the selected plan. */
    Purchase
}

enum class PaywallMessage {
    PurchaseFailed,
    Restored,
    NothingToRestore,
    RestoreFailed
}

sealed interface PaywallUiEvent {
    /** Open Play's purchase sheet for [offer], then report back through [PaywallViewModel.onPurchaseLaunched]. */
    data class LaunchPurchase(val offer: PaywallOffer) : PaywallUiEvent

    data class ShowMessage(val message: PaywallMessage) : PaywallUiEvent
}

/** The S2 Pro paywall: the user's entitlement, the plans Play sells and their prices, purchase and restore. */
@HiltViewModel(assistedFactory = PaywallViewModel.Factory::class)
class PaywallViewModel @AssistedInject constructor(
    @Assisted source: PaywallSource,
    entitlement: @JvmSuppressWildcards StateFlow<Entitlement>,
    private val billing: Billing,
    analytics: MonetisationAnalytics
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(source: PaywallSource): PaywallViewModel
    }

    private val selectedPlan = MutableStateFlow(PaywallPlan.Lifetime)
    private val restoring = MutableStateFlow(false)

    private val events = PendingEvents<PaywallUiEvent>()

    val uiState: StateFlow<PaywallUiState> = combine(entitlement, billing.offers, selectedPlan, restoring, events.flow) { entitlement, offers, plan, restoring, events ->
        PaywallUiState(entitlement.toStatus(), offers, plan, restoring, events)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaywallUiState(entitlement.value.toStatus(), billing.offers.value, selectedPlan.value)
    )

    init {
        analytics.paywallShown(source)
        if (billing.offers.value == PaywallOffers.Unavailable) billing.refreshOffers()
    }

    fun onSelectPlan(plan: PaywallPlan) {
        selectedPlan.value = plan
    }

    fun onPurchase() {
        val offer = uiState.value.selectedOffer ?: return
        events.post(PaywallUiEvent.LaunchPurchase(offer))
    }

    /** Whether Play's purchase sheet opened. The entitlement updates by itself once the purchase completes. */
    fun onPurchaseLaunched(launched: Boolean) {
        if (!launched) events.post(PaywallUiEvent.ShowMessage(PaywallMessage.PurchaseFailed))
    }

    fun onRestore() {
        if (restoring.value) return
        restoring.value = true
        viewModelScope.launch {
            val message = when (billing.restorePurchases()) {
                RestoreResult.Restored -> PaywallMessage.Restored
                RestoreResult.NothingToRestore -> PaywallMessage.NothingToRestore
                RestoreResult.Failed -> PaywallMessage.RestoreFailed
            }
            restoring.value = false
            events.post(PaywallUiEvent.ShowMessage(message))
        }
    }

    fun onRetry() {
        billing.refreshOffers()
    }

    fun onEventHandled(id: Long) = events.consume(id)
}

private fun Entitlement.toStatus(): PaywallStatus = when (this) {
    Entitlement.Unknown -> PaywallStatus.Checking
    is Entitlement.Free -> if (trialUsed) PaywallStatus.TrialEnded else PaywallStatus.TrialAvailable
    is Entitlement.Trial -> PaywallStatus.Trial(daysRemaining())
    is Entitlement.Pro -> PaywallStatus.Pro(source)
}
