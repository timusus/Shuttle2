package com.simplecityapps.shuttle.entitlement

/** Whether the user chose "Not now" on the empty Library's trial offer, which then never returns. */
interface TrialOfferDismissal {
    val dismissed: Boolean

    fun dismiss()
}
