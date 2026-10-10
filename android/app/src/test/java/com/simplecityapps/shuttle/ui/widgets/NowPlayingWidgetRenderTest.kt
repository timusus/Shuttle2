package com.simplecityapps.shuttle.ui.widgets

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionModifier
import androidx.glance.appwidget.action.RunCallbackAction
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.GlanceNodeMatcher
import androidx.glance.testing.unit.MappedNode
import androidx.glance.testing.unit.hasAnyDescendant
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.simplecityapps.core.R as CoreR
import com.simplecityapps.playback.PlaybackServiceAction
import com.simplecityapps.shuttle.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Renders [NowPlayingContent] directly with a fixed [NowPlayingWidgetState] and [WidgetLayout], bypassing the
 * live widget state and [widgetLayoutFor]'s size thresholds (covered by [WidgetLayoutTest]). Sizes below are
 * ones [WidgetLayoutTest] already asserts resolve to the named mode, so each fixture exercises real geometry.
 */
@RunWith(RobolectricTestRunner::class)
class NowPlayingWidgetRenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // One row, with art: title and artist on separate lines.
    private val rowLayout = widgetLayoutFor(DpSize(340.dp, 80.dp))

    // One row, too narrow for art or text: buttons only.
    private val rowButtonsOnlyLayout = widgetLayoutFor(DpSize(120.dp, 48.dp))

    // A 4x1 card compact enough that title and artist share one line.
    private val compactCardLayout = widgetLayoutFor(DpSize(388.dp, 100.dp))

    // A 4x2 split with room for the album on a third line.
    private val splitLayout = widgetLayoutFor(DpSize(388.dp, 212.dp))

    // A hero filling the widget with art.
    private val heroLayout = widgetLayoutFor(DpSize(388.dp, 430.dp))

    private fun playingState(
        isPlaying: Boolean = true,
        shuffleOn: Boolean = false,
        repeatMode: WidgetRepeatMode = WidgetRepeatMode.Off
    ) = NowPlayingWidgetState(
        hasTrack = true,
        title = "Song Title",
        artist = "Song Artist",
        album = "Song Album",
        isPlaying = isPlaying,
        shuffleOn = shuffleOn,
        repeatMode = repeatMode
    )

    /**
     * The button labelled [description] sends [action] to the playback service through [PlaybackActionCallback].
     * `CircleIconButton` (row/split/hero buttons) puts the content description on the icon `Image` and the click
     * action on its parent `Box`, so this matches either the described node itself or an ancestor of it.
     */
    private fun controlButton(
        description: String,
        action: PlaybackServiceAction
    ): GlanceNodeMatcher<MappedNode> {
        val describesButton = hasContentDescription(description).or(hasAnyDescendant(hasContentDescription(description)))
        return describesButton.and(sendsToPlaybackService(action))
    }

    private fun sendsToPlaybackService(action: PlaybackServiceAction): GlanceNodeMatcher<MappedNode> = GlanceNodeMatcher("runs PlaybackActionCallback for $action") { node ->
        node.value.emittable.modifier.any { element ->
            val callback = (element as? ActionModifier)?.action as? RunCallbackAction
            callback != null &&
                callback.callbackClass == PlaybackActionCallback::class.java &&
                callback.parameters[playbackActionKey] == action.name
        }
    }

    /** [NowPlayingContent] needs a real [Context] for its strings and drawables, which the test harness doesn't default. */
    private fun GlanceAppWidgetUnitTest.render(
        state: NowPlayingWidgetState,
        layout: WidgetLayout
    ) {
        setContext(context)
        provideComposable { GlanceTheme { NowPlayingContent(state, layout) } }
    }

    // region Track text

    @Test
    fun `a row with art shows title and artist on separate lines`() {
        runGlanceAppWidgetUnitTest {
            render(playingState(), rowLayout)
            onNode(hasText("Song Title")).assertExists()
            onNode(hasText("Song Artist")).assertExists()
        }
    }

    @Test
    fun `a compact card too short for two lines joins title and artist on one`() {
        runGlanceAppWidgetUnitTest {
            render(playingState(), compactCardLayout)
            onNode(hasText("Song Title · Song Artist")).assertExists()
        }
    }

    @Test
    fun `a split with room for three lines shows title, artist and album`() {
        runGlanceAppWidgetUnitTest {
            render(playingState(), splitLayout)
            onNode(hasText("Song Title")).assertExists()
            onNode(hasText("Song Artist")).assertExists()
            onNode(hasText("Song Album")).assertExists()
        }
    }

    @Test
    fun `a hero shows title and artist but not the album`() {
        runGlanceAppWidgetUnitTest {
            render(playingState(), heroLayout)
            onNode(hasText("Song Title")).assertExists()
            onNode(hasText("Song Artist")).assertExists()
            onNode(hasText("Song Album")).assertDoesNotExist()
        }
    }

    // endregion

    // region Play/pause

    @Test
    fun `the play pause button offers pause while playing, and toggles playback`() {
        runGlanceAppWidgetUnitTest {
            render(playingState(isPlaying = true), rowLayout)
            onNode(controlButton(context.getString(R.string.widget_pause), PlaybackServiceAction.TogglePlayback)).assertExists()
        }
    }

    @Test
    fun `the play pause button offers play while paused, and toggles playback`() {
        runGlanceAppWidgetUnitTest {
            render(playingState(isPlaying = false), rowLayout)
            onNode(controlButton(context.getString(R.string.widget_play), PlaybackServiceAction.TogglePlayback)).assertExists()
        }
    }

    @Test
    fun `the compact play button also reflects playing state and toggles playback`() {
        runGlanceAppWidgetUnitTest {
            render(playingState(isPlaying = false), compactCardLayout)
            onNode(controlButton(context.getString(R.string.widget_play), PlaybackServiceAction.TogglePlayback)).assertExists()
        }
    }

    // endregion

    // region Next/previous

    @Test
    fun `next and previous buttons target the right playback actions`() {
        runGlanceAppWidgetUnitTest {
            render(playingState(), rowLayout)
            onNode(controlButton(context.getString(R.string.button_skip_next), PlaybackServiceAction.SkipNext)).assertExists()
            onNode(controlButton(context.getString(R.string.button_skip_previous), PlaybackServiceAction.SkipPrevious)).assertExists()
        }
    }

    // endregion

    // region Shuffle/repeat

    @Test
    fun `shuffle reflects its state and toggles shuffle, on layouts that show it`() {
        runGlanceAppWidgetUnitTest {
            render(playingState(shuffleOn = true), splitLayout)
            onNode(controlButton(context.getString(CoreR.string.shuffle_on), PlaybackServiceAction.ToggleShuffle)).assertExists()
        }
        runGlanceAppWidgetUnitTest {
            render(playingState(shuffleOn = false), splitLayout)
            onNode(hasContentDescription(context.getString(CoreR.string.shuffle_off))).assertExists()
        }
    }

    @Test
    fun `repeat reflects off, all and one, and toggles repeat, on layouts that show it`() {
        listOf(
            WidgetRepeatMode.Off to R.string.widget_repeat_off,
            WidgetRepeatMode.All to R.string.widget_repeat_all,
            WidgetRepeatMode.One to R.string.widget_repeat_one
        ).forEach { (mode, expectedDescription) ->
            runGlanceAppWidgetUnitTest {
                render(playingState(repeatMode = mode), splitLayout)
                onNode(controlButton(context.getString(expectedDescription), PlaybackServiceAction.ToggleRepeat)).assertExists()
            }
        }
    }

    @Test
    fun `a row too narrow for shuffle and repeat doesn't show them`() {
        runGlanceAppWidgetUnitTest {
            render(playingState(), rowLayout)
            onNode(hasContentDescription(context.getString(CoreR.string.shuffle_off))).assertDoesNotExist()
            onNode(hasContentDescription(context.getString(R.string.widget_repeat_off))).assertDoesNotExist()
        }
    }

    // endregion

    // region Idle / empty state

    @Test
    fun `an idle state renders the app name and a prompt, without a track, on every layout`() {
        listOf(rowLayout, rowButtonsOnlyLayout, compactCardLayout, splitLayout, heroLayout).forEach { layout ->
            runGlanceAppWidgetUnitTest {
                render(NowPlayingWidgetState.Idle, layout)
                onNode(hasText(context.getString(R.string.app_name))).assertExists()
                onNode(hasText(context.getString(R.string.widget_idle_action))).assertExists()
            }
        }
    }

    // endregion
}
