package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.di.appGraph
import com.simplecityapps.shuttle.entitlement.ObservePaywallRequests
import com.simplecityapps.shuttle.entitlement.OnPaywallClosed
import com.simplecityapps.shuttle.entitlement.PaywallSource
import com.simplecityapps.shuttle.ui.common.ConsumeEvents
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import com.simplecityapps.shuttle.ui.shell.UtilityRoute
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import com.simplecityapps.trial.Billing
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import timber.log.Timber

/** The S2 Pro paywall, opened from [source]. */
@Serializable
data class PaywallRoute(
    val source: PaywallSource
) : UtilityRoute

/** The paywall's entry, for the shell's entry provider. */
fun EntryProviderScope<NavKey>.paywallEntries(navigator: AppNavigator) {
    entry<PaywallRoute> { route ->
        val context = LocalContext.current
        val onPaywallClosed = remember { context.appGraph<PaywallEntryPoint>().onPaywallClosed() }
        val scope = rememberCoroutineScope()
        PaywallEntry(
            route.source,
            onClose = {
                // Undispatched so the report is made before leaving cancels the scope
                scope.launch(start = CoroutineStart.UNDISPATCHED) { onPaywallClosed(route.source) }
                navigator.back()
            }
        )
    }
}

/**
 * Shows the paywall full-screen, over whatever's on screen, whenever [observePaywallRequests] reports a refused
 * action, while the lifecycle is at least STARTED.
 */
@Composable
fun PaywallHost(
    observePaywallRequests: ObservePaywallRequests,
    onPaywallClosed: OnPaywallClosed
) {
    var activeSource by rememberSaveable { mutableStateOf<PaywallSource?>(null) }
    // Closing removes the dialog, which clears its view model store and so reports the dismissal; a recreation keeps it
    val scope = rememberCoroutineScope()
    val close = {
        activeSource?.let { source -> scope.launch { onPaywallClosed(source) } }
        activeSource = null
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(observePaywallRequests, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // A request while it's showing leaves it as it is
            observePaywallRequests().collect { source -> if (activeSource == null) activeSource = source }
        }
    }
    activeSource?.let { source ->
        Dialog(
            onDismissRequest = close,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // Each visit gets its own view model store, so the paywall's view model lives and dies with the dialog
            CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
                S2AppTheme {
                    PaywallEntry(source, onClose = close)
                }
            }
        }
    }
}

/** Play's purchase sheet needs an activity, so the entry opens it rather than the ViewModel. */
@ContributesTo(AppScope::class)
interface PaywallEntryPoint {
    fun billing(): Billing

    fun onPaywallClosed(): OnPaywallClosed
}

/** The paywall wired to its ViewModel, for the shell's entry and the legacy dialog alike. */
@Composable
fun PaywallEntry(
    source: PaywallSource,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = assistedMetroViewModel<PaywallViewModel, PaywallViewModel.Factory> { create(source) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = LocalActivity.current
    val uriHandler = LocalUriHandler.current
    val billing = remember { context.appGraph<PaywallEntryPoint>().billing() }
    val scope = rememberCoroutineScope()
    ConsumeEvents(uiState.events, viewModel::onEventHandled) { event ->
        when (event) {
            is PaywallUiEvent.LaunchPurchase -> viewModel.onPurchaseLaunched(billing.launchPurchaseFlow(activity, event.offer))

            // Shown without holding back the events after it, such as a purchase launched while it's up
            is PaywallUiEvent.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(context.getString(event.message.text)) }

            PaywallUiEvent.TrialStarted, PaywallUiEvent.PurchaseCompleted -> onClose()
        }
    }
    PaywallScreen(
        uiState = uiState,
        onClose = onClose,
        onPurchase = viewModel::onPurchase,
        onRestore = viewModel::onRestore,
        onRetry = viewModel::onRetry,
        onManageSubscription = {
            val url = "https://play.google.com/store/account/subscriptions?package=${context.packageName}"
            runCatching { uriHandler.openUri(url) }.onFailure { Timber.w(it, "No app to open $url") }
        },
        onRedeemCode = { runCatching { uriHandler.openUri(PLAY_REDEEM_URL) }.onFailure { Timber.w(it, "No app to open $PLAY_REDEEM_URL") } },
        onStartTrial = viewModel::onStartTrial,
        onOpenPrivacyPolicy = { runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) }.onFailure { Timber.w(it, "No app to open $PRIVACY_POLICY_URL") } },
        modifier = modifier,
        snackbarHostState = snackbarHostState
    )
}

/** Play's own screen for entering a promo code; Play grants the purchase, and the entitlement updates by itself. */
private const val PLAY_REDEEM_URL = "https://play.google.com/redeem"

/** The privacy policy the Play listing links to. */
private const val PRIVACY_POLICY_URL = "https://shuttlemusicplayer.com/privacy"

@get:StringRes
private val PaywallMessage.text: Int
    get() = when (this) {
        PaywallMessage.PurchaseFailed -> R.string.paywall_purchase_failed
        PaywallMessage.Restored -> R.string.paywall_restored
        PaywallMessage.PurchasePending -> R.string.paywall_purchase_pending
        PaywallMessage.NothingToRestore -> R.string.paywall_nothing_to_restore
        PaywallMessage.RestoreFailed -> R.string.paywall_restore_failed
        PaywallMessage.ThankYou -> R.string.paywall_thank_you
        PaywallMessage.TrialStartFailed -> R.string.paywall_trial_start_failed
    }
