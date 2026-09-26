package com.simplecityapps.shuttle.ui.screens.library.folders

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.model.SongFolder

/** The folder's name, with storage volumes given friendly names. */
fun Folder.displayName(resources: Resources): String = if (path.size == 1) {
    when (name) {
        SongFolder.PRIMARY_VOLUME -> resources.getString(R.string.folders_internal_storage)
        SongFolder.OTHER_VOLUME -> resources.getString(R.string.folders_other)
        SongFolder.FILESYSTEM_ROOT -> resources.getString(R.string.folders_root)
        else -> name
    }
} else {
    name
}

/** Where the folder is, relative to its storage volume: "Music / Artist" rather than "primary / Music / Artist". */
fun Folder.displayPath(resources: Resources): String = if (path.size == 1) {
    displayName(resources)
} else {
    path.drop(1).joinToString(" / ")
}

@Composable
fun Folder.displayName(): String = displayName(LocalContext.current.resources)

@Composable
fun Folder.displayPath(): String = displayPath(LocalContext.current.resources)
