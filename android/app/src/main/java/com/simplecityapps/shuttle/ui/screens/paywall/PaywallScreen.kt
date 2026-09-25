package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Dns
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.ErrorState
import com.simplecityapps.shuttle.designsystem.component.S2Button
import com.simplecityapps.shuttle.designsystem.component.S2ButtonSize
import com.simplecityapps.shuttle.designsystem.component.S2ButtonStyle
import com.simplecityapps.shuttle.designsystem.component.S2LargeTopBar
import com.simplecityapps.shuttle.designsystem.component.S2SnackbarHost
import com.simplecityapps.shuttle.designsystem.component.StateAction
import com.simplecityapps.trial.PaywallOffers
import com.simplecityapps.trial.PaywallPlan
import com.simplecityapps.trial.ProSource
import com.squareup.phrase.Phrase

/**
 * The S2 Pro paywall: where the user stands, what Pro unlocks, and the plans with Play's prices. While Play's
 * prices load or can't be loaded the plans show placeholders, and nothing can be bought. A Pro user sees their
 * status instead of the plans, and a subscriber can manage their subscription.
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
            item(key = "status") { StatusCard(uiState.status) }
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
                        S2Button(
                            text = stringResource(R.string.paywall_purchase),
                            onClick = onPurchase,
                            size = S2ButtonSize.Medium,
                            enabled = uiState.selectedOffer != null,
                            modifier = Modifier.fillMaxWidth()
                        )
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
        }
    }
}

@Composable
private fun StatusCard(status: PaywallStatus) {
    val text = when (status) {
        PaywallStatus.TrialAvailable -> stringResource(R.string.paywall_status_trial_available)

        PaywallStatus.TrialEnded -> stringResource(R.string.paywall_status_trial_ended)

        is PaywallStatus.Trial -> Phrase.from(pluralStringResource(R.plurals.paywall_status_trial, status.daysLeft))
            .put("count", status.daysLeft)
            .format()
            .toString()

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
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Benefits() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(stringResource(R.string.paywall_benefits_heading))
        Benefit(Icons.Rounded.Dns, stringResource(R.string.paywall_benefit_streaming))
        Benefit(Icons.Rounded.CloudDownload, stringResource(R.string.paywall_benefit_downloads))
        Benefit(Icons.Rounded.CheckCircle, stringResource(R.string.paywall_benefit_free))
    }
}

@Composable
private fun Benefit(
    icon: ImageVector,
    text: String
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

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
    return Phrase.from(stringResource(pattern)).put("price", price).format().toString()
}
