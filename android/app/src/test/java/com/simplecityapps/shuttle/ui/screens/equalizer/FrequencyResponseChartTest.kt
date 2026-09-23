package com.simplecityapps.shuttle.ui.screens.equalizer

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FrequencyResponseChartTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val robot = FrequencyResponseChartRobot(composeTestRule)

    @Test
    fun `chart is displayed for a typical response`() {
        robot.setContent(sampleFrequencyResponse())
        robot.assertChartDisplayed()
    }

    @Test
    fun `chart is displayed with no data points`() {
        robot.setContent(emptyFrequencyResponse())
        robot.assertChartDisplayed()
    }

    @Test
    fun `dB axis shows gridline labels`() {
        robot.setContent(sampleFrequencyResponse())
        robot.assertTextDisplayed("-20 dB")
        robot.assertTextDisplayed("0 dB")
        robot.assertTextDisplayed("20 dB")
    }

    @Test
    fun `frequency axis shows standard EQ ticks`() {
        robot.setContent(sampleFrequencyResponse())
        robot.assertTextDisplayed("20 Hz")
        robot.assertTextDisplayed("1 kHz")
        robot.assertTextDisplayed("20 kHz")
    }

    @Test
    fun `frequency axis does not show ticks outside the plotted range`() {
        robot.setContent(sampleFrequencyResponse())
        robot.assertTextNotDisplayed("50 kHz")
    }
}
