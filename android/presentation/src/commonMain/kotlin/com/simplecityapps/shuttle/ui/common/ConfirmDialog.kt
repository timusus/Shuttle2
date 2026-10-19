package com.simplecityapps.shuttle.ui.common

enum class ConfirmEvent { Open, Confirm, Dismiss }

/** [confirmed] is true only on the step that should run the guarded action. */
data class ConfirmStep(val showing: Boolean, val confirmed: Boolean = false)

fun onConfirmEvent(showing: Boolean, event: ConfirmEvent): ConfirmStep = when (event) {
    ConfirmEvent.Open -> ConfirmStep(showing = true)
    ConfirmEvent.Confirm -> ConfirmStep(showing = false, confirmed = showing)
    ConfirmEvent.Dismiss -> ConfirmStep(showing = false)
}
