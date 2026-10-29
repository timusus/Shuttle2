package com.simplecityapps.mediaprovider

import android.content.Context
import android.net.ConnectivityManager
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ConnectivityMeteredNetworkTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `with no network at all the flow reports metered at once`() = runTest {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        shadowOf(connectivityManager).setDefaultNetworkActive(false)
        shadowOf(connectivityManager).setActiveNetworkInfo(null)

        val metered = withTimeoutOrNull(1_000) { ConnectivityMeteredNetwork(context).meteredFlow.first() }

        metered shouldBe true
    }
}
