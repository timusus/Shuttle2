package com.simplecityapps.shuttle.ui.screens.trial

import android.view.MenuItem
import android.widget.TextView
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.view.CircularProgressView
import com.simplecityapps.trial.Entitlement

/** True if the toolbar's trial chip shows: during the server trial, and after it ends so the paywall stays reachable. */
val Entitlement.showsTrialChip: Boolean
    get() = this is Entitlement.Trial || (this is Entitlement.Free && trialUsed)

/** Binds the toolbar's trial chip to [entitlement]: days left, and progress through the trial. */
fun MenuItem.bindTrialChip(entitlement: Entitlement) {
    isVisible = entitlement.showsTrialChip
    val daysRemaining = (entitlement as? Entitlement.Trial)?.daysRemaining() ?: 0
    actionView!!.findViewById<TextView>(R.id.daysRemaining).text = daysRemaining.toString()
    actionView!!.findViewById<CircularProgressView>(R.id.progress).setProgress(daysRemaining / Entitlement.TRIAL_LENGTH.inWholeDays.toFloat())
}
