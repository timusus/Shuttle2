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

    /**
     * Requested together: up to Android 9, deleting a song's file also needs write access, which is in the same storage
     * group as [name], so it adds no prompt of its own.
     */
    val names: Array<String>
        get() = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) arrayOf(name, Manifest.permission.WRITE_EXTERNAL_STORAGE) else arrayOf(name)

    fun isGranted(context: Context): Boolean = isGranted(context, name)

    /** The [names] not granted yet. */
    fun missing(context: Context): List<String> = names.filterNot { isGranted(context, it) }

    private fun isGranted(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
