package com.simplecityapps.shuttle.ui.common

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ConfirmDialogTest {
    @Test
    fun `opening shows the dialog without confirming`() {
        onConfirmEvent(showing = false, ConfirmEvent.Open) shouldBe ConfirmStep(showing = true, confirmed = false)
    }

    @Test
    fun `confirming closes the dialog and runs the action`() {
        onConfirmEvent(showing = true, ConfirmEvent.Confirm) shouldBe ConfirmStep(showing = false, confirmed = true)
    }

    @Test
    fun `dismissing closes the dialog without running the action`() {
        onConfirmEvent(showing = true, ConfirmEvent.Dismiss) shouldBe ConfirmStep(showing = false, confirmed = false)
    }

    @Test
    fun `confirming while closed does not run the action`() {
        onConfirmEvent(showing = false, ConfirmEvent.Confirm) shouldBe ConfirmStep(showing = false, confirmed = false)
    }
}
