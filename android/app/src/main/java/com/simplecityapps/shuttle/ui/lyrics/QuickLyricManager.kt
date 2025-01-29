package com.simplecityapps.shuttle.ui.lyrics

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object QuickLyricManager {
    fun isQuickLyricInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo("com.geecko.QuickLyric", PackageManager.GET_ACTIVITIES)
        true
    } catch (ignored: PackageManager.NameNotFoundException) {
        false
    }

    fun buildLyricsIntent(
        artistName: String,
        songName: String
    ): Intent = Intent("com.geecko.QuickLyric.getLyrics").apply {
        putExtra("TAGS", arrayOf(artistName, songName))
    }

    /**
     * @return true if the Play Store is available, and QuickLyric can be downloaded
     */
    fun canDownloadQuickLyric(context: Context): Boolean = quickLyricIntent.resolveActivity(context.packageManager) != null

    val quickLyricIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.geecko.QuickLyric"))
}
