package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.simplecityapps.mediaprovider.iconResId
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ErrorState
import com.simplecityapps.shuttle.designsystem.component.S2BottomActionBar
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2LargeTopBar
import com.simplecityapps.shuttle.designsystem.component.S2SnackbarHost
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.component.StateAction
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.ui.screens.sources.ServerTypes

/**
 * The S2 Pro paywall: the pitch and what's free, where the user stands, what Pro unlocks, and the lifetime price from
 * Play. The buy (or trial) button stays pinned at the bottom, so it's in reach wherever the user has scrolled.
 * While Play's price loads or can't be loaded the card shows a placeholder, and nothing can be bought; nor can it
 * while a purchase waits on payment. A user who hasn't had the trial is offered it first, with buying second.
 * Everyone without Pro can restore purchases and redeem a Play promo code. A Pro user sees their status instead of
 * the offer, and a subscriber can manage their subscription.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    uiState: PaywallUiState,
    onClose: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onRedeemCode: () -> Unit,
    onRetry: () -> Unit,
    onManageSubscription: () -> Unit,
    onStartTrial: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val viewState = paywallViewState(uiState)
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { S2LargeTopBar(title = stringResource(R.string.paywall_title), onBack = onClose, scrollBehavior = scrollBehavior) },
        bottomBar = {
            viewState.purchaseBar?.let { PurchaseBar(it, onPurchase, onStartTrial) }
        },
        snackbarHost = { S2SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = S2Spacing.medium, vertical = S2Spacing.small),
            verticalArrangement = Arrangement.spacedBy(S2Spacing.medium)
        ) {
            item(key = "pitch") { Pitch() }
            item(key = "status") { StatusCard(viewState.status, viewState.explainsTrialEnd) }
            item(key = "benefits") { Benefits() }
            if (viewState.showManageSubscription) {
                item(key = "manage") {
                    S2Button(
                        text = stringResource(R.string.paywall_manage_subscription),
                        onClick = onManageSubscription,
                        style = S2ButtonStyle.Tonal,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            viewState.offer?.let { offer -> item(key = "offer") { LifetimeOffer(offer) } }
            if (viewState.showAccountActions) {
                if (viewState.showPricesError) {
                    item(key = "error") {
                        ErrorState(
                            title = stringResource(R.string.paywall_prices_unavailable),
                            message = stringResource(R.string.paywall_prices_unavailable_message),
                            action = StateAction(stringResource(R.string.paywall_retry), onRetry)
                        )
                    }
                }
                item(key = "account") {
                    Column(verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
                        S2Button(
                            text = stringResource(R.string.paywall_restore),
                            onClick = onRestore,
                            style = S2ButtonStyle.Text,
                            enabled = viewState.restoreEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                        S2Button(
                            text = stringResource(R.string.paywall_redeem_code),
                            onClick = onRedeemCode,
                            style = S2ButtonStyle.Text,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            item(key = "footer") {
                S2Button(
                    text = stringResource(R.string.paywall_privacy_policy),
                    onClick = onOpenPrivacyPolicy,
                    style = S2ButtonStyle.Text,
                    size = S2ButtonSize.ExtraSmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** The pitch, then what's free and what Pro costs, in the first lines as Play's disclosure rules expect. */
@Composable
private fun Pitch() {
    Column(verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
        S2Text(stringResource(R.string.paywall_headline), style = MaterialTheme.typography.headlineSmall)
        S2Text(
            stringResource(R.string.paywall_disclosure),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The main action, pinned below the list: start the trial with buying second, or buy lifetime Pro. */
@Composable
private fun PurchaseBar(
    bar: PaywallViewState.PurchaseBar,
    onPurchase: () -> Unit,
    onStartTrial: () -> Unit
) {
    S2BottomActionBar {
        when (bar.action) {
            PaywallPrimaryAction.StartTrial -> {
                S2Button(
                    text = stringResource(R.string.paywall_start_trial),
                    onClick = onStartTrial,
                    size = S2ButtonSize.Medium,
                    modifier = Modifier.fillMaxWidth()
                )
                S2Button(
                    text = stringResource(R.string.paywall_buy_now),
                    onClick = onPurchase,
                    style = S2ButtonStyle.Outlined,
                    size = S2ButtonSize.Medium,
                    enabled = bar.purchaseEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            PaywallPrimaryAction.Purchase -> S2Button(
                text = stringResource(R.string.paywall_purchase),
                onClick = onPurchase,
                size = S2ButtonSize.Medium,
                enabled = bar.purchaseEnabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusCard(
    status: PaywallViewState.StatusMessage,
    explainsTrialEnd: Boolean
) {
    val text = when (status) {
        PaywallViewState.StatusMessage.Checking -> stringResource(R.string.paywall_status_checking)
        PaywallViewState.StatusMessage.TrialAvailable -> stringResource(R.string.paywall_status_trial_available)
        PaywallViewState.StatusMessage.TrialEnded -> stringResource(R.string.paywall_status_trial_ended)
        is PaywallViewState.StatusMessage.Trial -> pluralStringResource(R.plurals.paywall_status_trial, status.daysLeft, status.daysLeft)
        PaywallViewState.StatusMessage.PurchasePending -> stringResource(R.string.paywall_status_purchase_pending)
        PaywallViewState.StatusMessage.Pro -> stringResource(R.string.paywall_status_pro)
        PaywallViewState.StatusMessage.ProLegacy -> stringResource(R.string.paywall_status_pro_legacy)
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(S2Spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(S2Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, modifier = Modifier.size(HeroIconSize))
            Column(verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
                S2Text(text, style = MaterialTheme.typography.bodyLarge)
                if (explainsTrialEnd) S2Text(stringResource(R.string.paywall_trial_terms), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Benefits() {
    Column(verticalArrangement = Arrangement.spacedBy(S2Spacing.smallMedium)) {
        SectionTitle(stringResource(R.string.paywall_benefits_heading))
        // Offline and the car lead: that's what Plex Pass and Emby Premiere charge for, while streaming alone is free elsewhere
        Benefit(stringResource(R.string.paywall_benefit_downloads)) { BenefitIcon(Icons.Rounded.CloudDownload) }
        Benefit(stringResource(R.string.paywall_benefit_android_auto)) { BenefitIcon(Icons.Rounded.DirectionsCar) }
        Benefit(stringResource(R.string.paywall_benefit_streaming)) { ServerMarks() }
        Benefit(stringResource(R.string.paywall_benefit_batch_tag_edit)) { BenefitIcon(Icons.Rounded.Edit) }
        Benefit(stringResource(R.string.paywall_benefit_replay_gain)) { BenefitIcon(Icons.Rounded.GraphicEq) }
        Benefit(stringResource(R.string.paywall_benefit_free)) { BenefitIcon(Icons.Rounded.CheckCircle) }
    }
}

/** A benefit, with [leading] centred in a slot wide enough for the three server marks, so every row's text lines up. */
@Composable
private fun Benefit(
    text: String,
    leading: @Composable () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(S2Spacing.medium), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(BenefitLeadingWidth), contentAlignment = Alignment.Center) { leading() }
        S2Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BenefitIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
}

/** The Jellyfin, Emby, Plex and Subsonic marks, in their own colours. Decorative: the benefit's text names them. */
@Composable
private fun ServerMarks() {
    Row(horizontalArrangement = Arrangement.spacedBy(ServerMarkSpacing)) {
        ServerTypes.forEach { type ->
            Image(painterResource(type.iconResId()), contentDescription = null, modifier = Modifier.size(ServerMarkSize))
        }
    }
}

private val ServerMarkSize = 20.dp
private val ServerMarkSpacing = 4.dp
private val BenefitLeadingWidth = ServerMarkSize * ServerTypes.size + ServerMarkSpacing * (ServerTypes.size - 1)

/** The premium glyph beside the heading. */
private val HeroIconSize = 32.dp

@Composable
private fun SectionTitle(text: String) {
    S2Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** The one-time unlock with Play's price, or a placeholder while prices load or can't be loaded. */
@Composable
private fun LifetimeOffer(offer: PaywallViewState.Offer) {
    Column(verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
        SectionTitle(stringResource(R.string.paywall_offer_heading))
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(S2Spacing.medium)) {
                S2Text(stringResource(R.string.paywall_plan_lifetime), style = MaterialTheme.typography.titleMedium)
                S2Text(
                    when (offer) {
                        is PaywallViewState.Offer.Price -> stringResource(R.string.paywall_price_once, offer.formatted)
                        PaywallViewState.Offer.Loading -> stringResource(R.string.paywall_price_loading)
                        PaywallViewState.Offer.Unavailable -> stringResource(R.string.paywall_price_unavailable)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
