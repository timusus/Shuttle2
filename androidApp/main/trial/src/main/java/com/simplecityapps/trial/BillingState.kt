package com.simplecityapps.trial

sealed class BillingState {
    object Unknown : BillingState()
    object Paid : BillingState()
    object Unpaid : BillingState()
}