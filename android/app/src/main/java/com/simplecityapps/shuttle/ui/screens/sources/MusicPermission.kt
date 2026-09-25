package com.simplecityapps.shuttle.ui.screens.sources

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** The runtime permission that lets MediaStore list this device's audio. */
object MusicPermission {
    val name: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun isGranted(context: Context): Boolean = ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
}

/** Where the music permission stands. */
enum class MusicAccess {
    NotRequested,

    /** Refused, but the system will still show the prompt again. */
    Denied,

    /** Refused for good: only the app's system settings page can grant it now. */
    PermanentlyDenied,
    Granted;

    companion object {
        fun of(granted: Boolean, requested: Boolean, showRationale: Boolean): MusicAccess = when {
            granted -> Granted
            !requested -> NotRequested
            showRationale -> Denied
            else -> PermanentlyDenied
        }
    }
}
