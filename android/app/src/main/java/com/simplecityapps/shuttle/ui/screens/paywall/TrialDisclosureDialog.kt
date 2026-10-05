package com.simplecityapps.shuttle.ui.screens.paywall

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.designsystem.component.S2Dialog
import com.simplecityapps.shuttle.designsystem.component.S2Text
import com.simplecityapps.shuttle.designsystem.theme.S2Spacing
import com.simplecityapps.shuttle.entitlement.ProFeature
import com.simplecityapps.shuttle.entitlement.TrialDisclosures

/**
 * Tells the user, once, that their first use of a Shuttle Music Pro feature started the free trial. A trial started
 * while the app wasn't on screen (in the car, from the notification) is disclosed the next time it is.
 */
@Composable
fun TrialDisclosureHost(trialDisclosures: TrialDisclosures) {
    val pending by trialDisclosures.pending.collectAsStateWithLifecycle()
    pending?.let { feature -> TrialDisclosureDialog(feature, onDismiss = trialDisclosures::onDisclosed) }
}

@Composable
fun TrialDisclosureDialog(
    feature: ProFeature,
    onDismiss: () -> Unit
) {
    S2Dialog(
        title = stringResource(R.string.trial_disclosure_title),
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.trial_disclosure_ok),
        onConfirm = onDismiss,
        icon = Icons.Rounded.WorkspacePremium
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(S2Spacing.small)) {
            S2Text(stringResource(feature.disclosureLead))
            S2Text(stringResource(R.string.trial_disclosure_body))
        }
    }
}

@get:StringRes
private val ProFeature.disclosureLead: Int
    get() = when (this) {
        ProFeature.Servers -> R.string.trial_disclosure_lead_servers
        ProFeature.AndroidAuto -> R.string.trial_disclosure_lead_android_auto
        ProFeature.BatchTagEdit -> R.string.trial_disclosure_lead_batch_tag_edit
        ProFeature.AdvancedAudio -> R.string.trial_disclosure_lead_advanced_audio
    }
