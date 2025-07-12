package com.simplecityapps.shuttle.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.simplecityapps.shuttle.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutHelper @Inject constructor() {

    companion object {
        const val SHORTCUT_ID_TOGGLE_PLAYBACK = "toggle_playback"
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun createPlaybackShortcut(context: Context, isPlaying: Boolean) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        val intent = Intent(context, ShortcutHandlerActivity::class.java).apply {
            action = ShortcutHandlerActivity.ACTION_TOGGLE_PLAYBACK
        }

        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID_TOGGLE_PLAYBACK)
            .setShortLabel(context.getString(R.string.button_toggle_playback))
            .setLongLabel(context.getString(R.string.button_toggle_playback))
            .setIcon(
                Icon.createWithResource(
                    context,
                    if (isPlaying) com.simplecityapps.playback.R.drawable.ic_pause_black_24dp else com.simplecityapps.playback.R.drawable.ic_play_arrow_black_24dp
                )
            )
            .setIntent(intent)
            .build()

        shortcutManager.dynamicShortcuts = listOf(shortcut)
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun updatePlaybackShortcut(context: Context, isPlaying: Boolean) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        val intent = Intent(context, ShortcutHandlerActivity::class.java).apply {
            action = ShortcutHandlerActivity.ACTION_TOGGLE_PLAYBACK
        }

        val shortcut = ShortcutInfo.Builder(context, SHORTCUT_ID_TOGGLE_PLAYBACK)
            .setShortLabel(context.getString(R.string.button_toggle_playback))
            .setLongLabel(context.getString(R.string.button_toggle_playback))
            .setIcon(
                Icon.createWithResource(
                    context,
                    if (isPlaying) com.simplecityapps.playback.R.drawable.ic_pause_black_24dp else com.simplecityapps.playback.R.drawable.ic_play_arrow_black_24dp
                )
            )
            .setIntent(intent)
            .build()

        shortcutManager.updateShortcuts(listOf(shortcut))
    }
}
