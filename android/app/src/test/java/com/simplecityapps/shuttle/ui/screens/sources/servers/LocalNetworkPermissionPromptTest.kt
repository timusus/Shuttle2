package com.simplecityapps.shuttle.ui.screens.sources.servers

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalNetworkPermissionPromptTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val asked = mutableListOf<String>()
    private val answers = mutableListOf<Boolean>()
    private var requested by mutableStateOf(false)

    /** Records each permission asked for and answers it with [grant]. */
    private class FakeRegistry(private val asked: MutableList<String>, private val grant: () -> Boolean) : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            asked += input as String
            dispatchResult(requestCode, grant())
        }
    }

    private fun setContent(grant: () -> Boolean) {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = FakeRegistry(asked, grant)
        }
        rule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                LocalNetworkPermissionPrompt(requested) { answers += it }
            }
        }
    }

    @Test
    fun `nothing is asked until the sign-in holds LAN access for it`() {
        setContent { true }
        rule.waitForIdle()

        asked shouldBe emptyList()
    }

    @Test
    fun `a request asks for the local-network permission and reports the answer`() {
        setContent { false }
        requested = true
        rule.waitForIdle()

        asked shouldBe listOf(LocalNetworkPermission.NAME)
        answers shouldBe listOf(false)
    }

    @Test
    fun `a second request, after Retry, asks again`() {
        setContent { true }
        requested = true
        rule.waitForIdle()
        requested = false
        rule.waitForIdle()
        requested = true
        rule.waitForIdle()

        asked.size shouldBe 2
        answers shouldBe listOf(true, true)
    }
}
