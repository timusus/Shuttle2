package com.simplecityapps.shuttle.ui.actions

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.simplecityapps.createSong
import com.simplecityapps.fakes.FakeSongRepository
import com.simplecityapps.fakes.TestMediaActions
import com.simplecityapps.shuttle.model.MediaProviderType
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

/** The system delete dialog's answer reaches the request across recreation and screen changes; a finishing activity declines it. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SystemDeleteRequestReceiverTest {
    private val confirmations = ConfirmationHandoff<IntentSender>()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val scenario = ActivityScenario.launch(ComponentActivity::class.java)

    @After
    fun tearDown() {
        scope.cancel()
        scenario.close()
    }

    private fun receiver() = scenario.onActivity { SystemDeleteRequestReceiver(it, confirmations) }

    private fun intentSender() = PendingIntent.getActivity(ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE).intentSender

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun launchedRequestCode(): Int {
        var requestCode = 0
        scenario.onActivity { requestCode = shadowOf(it).lastIntentSenderRequest.requestCode }
        return requestCode
    }

    /** Asks for a confirmation and returns it with the request code the receiver launched its dialog under. */
    private fun confirmThroughReceiver(): Pair<Deferred<Boolean>, Int> {
        receiver()
        val confirmed = scope.async { confirmations.confirm(intentSender()) }
        idle()
        return confirmed to launchedRequestCode()
    }

    private fun answer(
        requestCode: Int,
        resultCode: Int,
    ) {
        scenario.onActivity { it.activityResultRegistry.dispatchResult(requestCode, resultCode, null) }
        idle()
    }

    @Test
    fun `the user's answer completes the request`() {
        val (confirmed, requestCode) = confirmThroughReceiver()

        answer(requestCode, Activity.RESULT_OK)

        confirmed.getCompleted() shouldBe true
    }

    @Test
    fun `an answer that reaches the recreated activity completes the request`() {
        val (confirmed, requestCode) = confirmThroughReceiver()

        scenario.recreate()
        confirmed.isCompleted shouldBe false
        // The answer comes back before the recreated activity creates its receiver, as it does on a real device
        answer(requestCode, Activity.RESULT_OK)
        receiver()
        idle()

        confirmed.getCompleted() shouldBe true
    }

    @Test
    fun `finishing the activity declines its dialog`() {
        val (confirmed, _) = confirmThroughReceiver()

        // Finishes the activity
        scenario.moveToState(Lifecycle.State.DESTROYED)

        confirmed.getCompleted() shouldBe false
    }

    @Test
    fun `an accepted delete still removes the song after the requesting screen left composition`() {
        val songRepository = FakeSongRepository()
        val actions = TestMediaActions(songRepository = songRepository)
        actions.mediaStoreDeleter = MediaStoreSongDeleter { songs, _ -> if (confirmations.confirm(intentSender())) songs.toSet() else emptySet() }
        val song = createSong(id = 1, path = "content://a").copy(mediaProvider = MediaProviderType.MediaStore, externalId = "10")
        receiver()
        scenario.onActivity {
            it.setContent { LaunchedEffect(Unit) { actions.deleteSongs(MediaSelection.Songs(song)) } }
        }
        idle()
        val requestCode = launchedRequestCode()

        // The screen leaves composition while the system dialog shows
        scenario.onActivity { it.setContent { } }
        idle()
        answer(requestCode, Activity.RESULT_OK)

        songRepository.removed shouldBe listOf(song)
    }
}
