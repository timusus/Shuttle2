package com.simplecityapps.shuttle.ui.common.mediaactions

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.simplecityapps.shuttle.ui.actions.ConfirmationHandoff
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The system delete dialog's answer reaches the request across activity recreation, and a finishing activity declines it. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SystemDeleteRequestLauncherTest {
    private val confirmations = ConfirmationHandoff<IntentSender>()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val scenario = ActivityScenario.launch(ComponentActivity::class.java)

    @After
    fun tearDown() {
        scope.cancel()
        scenario.close()
    }

    private fun host() = scenario.onActivity { it.setContent { SystemDeleteRequestLauncher(confirmations) } }

    /** Asks for a confirmation and returns it with the request code the host launched its dialog under. */
    private fun confirmThroughHost(): Pair<Deferred<Boolean>, Int> {
        host()
        val intentSender = PendingIntent.getActivity(ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE).intentSender
        val confirmed = scope.async { confirmations.confirm(intentSender) }
        shadowOf(Looper.getMainLooper()).idle()
        var requestCode = 0
        scenario.onActivity { requestCode = shadowOf(it).lastIntentSenderRequest.requestCode }
        return confirmed to requestCode
    }

    private fun answer(
        requestCode: Int,
        resultCode: Int,
    ) {
        scenario.onActivity { it.activityResultRegistry.dispatchResult(requestCode, resultCode, null) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `the user's answer completes the request`() {
        val (confirmed, requestCode) = confirmThroughHost()

        answer(requestCode, Activity.RESULT_OK)

        confirmed.getCompleted() shouldBe true
    }

    @Test
    fun `an answer that reaches the recreated activity completes the request`() {
        val (confirmed, requestCode) = confirmThroughHost()

        scenario.recreate()
        confirmed.isCompleted shouldBe false
        // The answer comes back before the recreated activity composes, as it does on a real device
        answer(requestCode, Activity.RESULT_OK)
        host()
        shadowOf(Looper.getMainLooper()).idle()

        confirmed.getCompleted() shouldBe true
    }

    @Test
    fun `finishing the activity declines its dialog`() {
        val (confirmed, _) = confirmThroughHost()

        // Finishes the activity
        scenario.moveToState(Lifecycle.State.DESTROYED)

        confirmed.getCompleted() shouldBe false
    }
}
