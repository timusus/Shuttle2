package com.simplecityapps.shuttle.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.simplecityapps.shuttle.R
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class ShortcutHelper @Inject constructor() {

    companion object {
        const val SHORTCUT_ID_TOGGLE_PLAYBACK = "toggle_playback"
        const val SHORTCUT_ID_SHUFFLE_ALL = "shuffle_all"
        const val SHORTCUT_ID_SEARCH = "search"
    }

    /** Sets the app's shortcuts: play/pause for [isPlaying], shuffle all and search. */
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun createPlaybackShortcut(context: Context, isPlaying: Boolean) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        shortcutManager.dynamicShortcuts = listOf(playbackShortcut(context, isPlaying), shuffleAllShortcut(context), searchShortcut(context))
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun updatePlaybackShortcut(context: Context, isPlaying: Boolean) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        shortcutManager.updateShortcuts(listOf(playbackShortcut(context, isPlaying)))
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun playbackShortcut(context: Context, isPlaying: Boolean): ShortcutInfo = shortcut(
        context = context,
        id = SHORTCUT_ID_TOGGLE_PLAYBACK,
        label = context.getString(R.string.button_toggle_playback),
        iconRes = if (isPlaying) com.simplecityapps.playback.R.drawable.ic_pause_black_24dp else com.simplecityapps.playback.R.drawable.ic_play_arrow_black_24dp,
        intent = Intent(context, ShortcutHandlerActivity::class.java).setAction(ShortcutHandlerActivity.ACTION_TOGGLE_PLAYBACK)
    )

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun shuffleAllShortcut(context: Context): ShortcutInfo = shortcut(
        context = context,
        id = SHORTCUT_ID_SHUFFLE_ALL,
        label = context.getString(R.string.home_shuffle_all),
        iconRes = com.simplecityapps.playback.R.drawable.ic_shuffle_black_24dp,
        intent = Intent(context, ShortcutHandlerActivity::class.java).setAction(ShortcutHandlerActivity.ACTION_SHUFFLE_ALL)
    )

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun searchShortcut(context: Context): ShortcutInfo = shortcut(
        context = context,
        id = SHORTCUT_ID_SEARCH,
        label = context.getString(R.string.shell_tab_search),
        iconRes = R.drawable.ic_shortcut_search_24dp,
        intent = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_SEARCH)
    )

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun shortcut(context: Context, id: String, label: String, iconRes: Int, intent: Intent): ShortcutInfo = ShortcutInfo.Builder(context, id)
        .setShortLabel(label)
        .setLongLabel(label)
        .setIcon(Icon.createWithResource(context, iconRes))
        .setIntent(intent)
        .build()
}
