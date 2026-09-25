package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.shell.AppNavigator
import com.simplecityapps.shuttle.ui.theme.S2AppTheme
import com.simplecityapps.trial.Billing
import com.simplecityapps.trial.PaywallSource
import com.simplecityapps.trial.ServerAccessGate
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import timber.log.Timber

/** The S2 Pro paywall, opened from [source]. */
@Serializable
data class PaywallRoute(
    val source: PaywallSource
) : NavKey

/** The paywall's entry, for the shell's entry provider. */
fun EntryProviderScope<NavKey>.paywallEntries(navigator: AppNavigator) {
    entry<PaywallRoute> { route -> PaywallEntry(route.source, onClose = { navigator.back() }) }
}

/**
 * Shows the paywall full-screen, over whatever's on screen, whenever [serverAccessGate] refuses an action, while the
 * lifecycle is at least STARTED.
 */
@Composable
fun PaywallHost(serverAccessGate: ServerAccessGate) {
    var activeSource by rememberSaveable { mutableStateOf<PaywallSource?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(serverAccessGate, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // A request while it's showing leaves it as it is
            serverAccessGate.paywallRequests.collect { source -> if (activeSource == null) activeSource = source }
        }
    }
    activeSource?.let { source ->
        Dialog(
            onDismissRequest = { activeSource = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            S2AppTheme {
                PaywallEntry(source, onClose = { activeSource = null })
            }
        }
    }
}

/** Play's purchase sheet needs an activity, so the entry opens it rather than the ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PaywallEntryPoint {
    fun billing(): Billing
}

/** The paywall wired to its ViewModel, for the shell's entry and the legacy dialog alike. */
@Composable
fun PaywallEntry(
    source: PaywallSource,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = hiltViewModel<PaywallViewModel, PaywallViewModel.Factory> { it.create(source) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = LocalActivity.current
    val uriHandler = LocalUriHandler.current
    val billing = remember { EntryPointAccessors.fromApplication<PaywallEntryPoint>(context.applicationContext).billing() }
    val currentActivity by rememberUpdatedState(activity)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PaywallUiEvent.LaunchPurchase -> viewModel.onPurchaseLaunched(currentActivity?.let { billing.launchPurchaseFlow(it, event.offer) } ?: false)
                is PaywallUiEvent.ShowMessage -> launch { snackbarHostState.showSnackbar(context.getString(event.message.text)) }
            }
        }
    }
    PaywallScreen(
        uiState = uiState,
        onClose = onClose,
        onSelectPlan = viewModel::onSelectPlan,
        onPurchase = viewModel::onPurchase,
        onRestore = viewModel::onRestore,
        onRetry = viewModel::onRetry,
        onManageSubscription = {
            val url = "https://play.google.com/store/account/subscriptions?package=${context.packageName}"
            runCatching { uriHandler.openUri(url) }.onFailure { Timber.w(it, "No app to open $url") }
        },
        modifier = modifier,
        snackbarHostState = snackbarHostState
    )
}

@get:StringRes
private val PaywallMessage.text: Int
    get() = when (this) {
        PaywallMessage.PurchaseFailed -> R.string.paywall_purchase_failed
        PaywallMessage.Restored -> R.string.paywall_restored
        PaywallMessage.NothingToRestore -> R.string.paywall_nothing_to_restore
        PaywallMessage.RestoreFailed -> R.string.paywall_restore_failed
    }
