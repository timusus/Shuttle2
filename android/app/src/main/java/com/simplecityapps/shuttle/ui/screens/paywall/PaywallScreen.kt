package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.simplecityapps.mediaprovider.iconResId
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ErrorState
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2LargeTopBar
import com.simplecityapps.shuttle.designsystem.component.S2SnackbarHost
import com.simplecityapps.shuttle.designsystem.component.StateAction
import com.simplecityapps.shuttle.ui.screens.sources.ServerTypes
import com.simplecityapps.trial.PaywallOffers
import com.simplecityapps.trial.PaywallPlan
import com.simplecityapps.trial.ProSource

/**
 * The S2 Pro paywall: where the user stands, what Pro unlocks, and the plans with Play's prices. While Play's
 * prices load or can't be loaded the plans show placeholders, and nothing can be bought. A user who hasn't had the
 * trial is offered it first, with buying second. A Pro user sees their status instead of the plans, and a subscriber
 * can manage their subscription.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    uiState: PaywallUiState,
    onClose: () -> Unit,
    onSelectPlan: (PaywallPlan) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onManageSubscription: () -> Unit,
    onStartTrial: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { S2LargeTopBar(title = stringResource(R.string.paywall_title), onBack = onClose, scrollBehavior = scrollBehavior) },
        snackbarHost = { S2SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "status") { StatusCard(uiState.status, uiState.explainsTrialEnd) }
            item(key = "benefits") { Benefits() }
            val status = uiState.status
            if (status is PaywallStatus.Pro) {
                if (status.source == ProSource.Subscription || status.source == ProSource.LegacySubscription) {
                    item(key = "manage") {
                        S2Button(
                            text = stringResource(R.string.paywall_manage_subscription),
                            onClick = onManageSubscription,
                            style = S2ButtonStyle.Tonal,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                item(key = "plans") { Plans(uiState, onSelectPlan) }
                if (uiState.offers == PaywallOffers.Unavailable) {
                    item(key = "error") {
                        ErrorState(
                            title = stringResource(R.string.paywall_prices_unavailable),
                            message = stringResource(R.string.paywall_prices_unavailable_message),
                            action = StateAction(stringResource(R.string.paywall_retry), onRetry)
                        )
                    }
                }
                item(key = "purchase") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (uiState.primaryAction) {
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
                                    enabled = uiState.selectedOffer != null,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            PaywallPrimaryAction.Purchase -> S2Button(
                                text = stringResource(R.string.paywall_purchase),
                                onClick = onPurchase,
                                size = S2ButtonSize.Medium,
                                enabled = uiState.selectedOffer != null,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        S2Button(
                            text = stringResource(R.string.paywall_restore),
                            onClick = onRestore,
                            style = S2ButtonStyle.Text,
                            enabled = !uiState.restoring,
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

@Composable
private fun StatusCard(
    status: PaywallStatus,
    explainsTrialEnd: Boolean
) {
    val text = when (status) {
        PaywallStatus.Checking -> stringResource(R.string.paywall_status_checking)
        PaywallStatus.TrialAvailable -> stringResource(R.string.paywall_status_trial_available)
        PaywallStatus.TrialEnded -> stringResource(R.string.paywall_status_trial_ended)
        is PaywallStatus.Trial -> pluralStringResource(R.plurals.paywall_status_trial, status.daysLeft, status.daysLeft)
        is PaywallStatus.Pro -> stringResource(R.string.paywall_status_pro)
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                if (explainsTrialEnd) Text(stringResource(R.string.paywall_trial_terms), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Benefits() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.paywall_benefits_heading))
        Benefit(stringResource(R.string.paywall_benefit_streaming)) { ServerMarks() }
        Benefit(stringResource(R.string.paywall_benefit_downloads)) { BenefitIcon(Icons.Rounded.CloudDownload) }
        Benefit(stringResource(R.string.paywall_benefit_free)) { BenefitIcon(Icons.Rounded.CheckCircle) }
    }
}

/** A benefit, with [leading] centred in a slot wide enough for the three server marks, so every row's text lines up. */
@Composable
private fun Benefit(
    text: String,
    leading: @Composable () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(BenefitLeadingWidth), contentAlignment = Alignment.Center) { leading() }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BenefitIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
}

/** The Jellyfin, Emby and Plex marks, in their own colours. Decorative: the benefit's text names them. */
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
private val BenefitLeadingWidth = ServerMarkSize * 3 + ServerMarkSpacing * 2

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** One card per plan: Play's offers once they've loaded, otherwise every plan with a placeholder price. */
@Composable
private fun Plans(
    uiState: PaywallUiState,
    onSelectPlan: (PaywallPlan) -> Unit
) {
    val offers = uiState.offers
    val plans = if (offers is PaywallOffers.Available) offers.offers.map { it.plan } else PaywallPlan.entries
    val selected = uiState.selectedOffer?.plan
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.paywall_plans_heading))
        plans.forEach { plan ->
            val price = uiState.available.firstOrNull { it.plan == plan }?.formattedPrice
            PlanCard(
                plan = plan,
                price = when {
                    price != null -> plan.priceText(price)
                    offers == PaywallOffers.Loading -> stringResource(R.string.paywall_price_loading)
                    else -> stringResource(R.string.paywall_price_unavailable)
                },
                selected = plan == selected,
                enabled = price != null,
                onClick = { onSelectPlan(plan) }
            )
            if (plan == PaywallPlan.Annual) {
                Text(
                    stringResource(R.string.paywall_annual_terms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: PaywallPlan,
    price: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(horizontal = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(plan.title), style = MaterialTheme.typography.titleMedium)
                Text(price, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (plan == PaywallPlan.Lifetime) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.paywall_plan_best_value),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
    if (selected) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) { content() }
    } else {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

private val PaywallPlan.title: Int
    get() = when (this) {
        PaywallPlan.Lifetime -> R.string.paywall_plan_lifetime
        PaywallPlan.Annual -> R.string.paywall_plan_annual
    }

@Composable
private fun PaywallPlan.priceText(price: String): String {
    val pattern = when (this) {
        PaywallPlan.Lifetime -> R.string.paywall_price_once
        PaywallPlan.Annual -> R.string.purchase_price_annual
    }
    return stringResource(pattern, price)
}
