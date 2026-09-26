package com.simplecityapps.imageloading.coil.source

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import java.io.FileNotFoundException
import org.robolectric.Robolectric

/**
 * A fake `content://media` provider, registered under Robolectric so [openMediaStoreAudioThumbnail]'s call to
 * `ContentResolver.openTypedAssetFile` reaches [behavior] instead of a real MediaProvider.
 */
class FakeMediaAudioProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: Bundle?,
        signal: CancellationSignal?
    ): AssetFileDescriptor {
        requestedIds += ContentUris.parseId(uri)
        return behavior(uri)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    companion object {
        /** The MediaStore audio id from every request made since the last [reset]. */
        val requestedIds: MutableList<Long> = mutableListOf()

        /** What to return (or throw) for the next request. */
        var behavior: (Uri) -> AssetFileDescriptor = { throw FileNotFoundException("not configured") }

        fun reset() {
            requestedIds.clear()
            behavior = { throw FileNotFoundException("not configured") }
        }

        /** Registers this provider under MediaStore's "media" authority for the current test. */
        fun register() {
            reset()
            Robolectric.buildContentProvider(FakeMediaAudioProvider::class.java).create("media")
        }
    }
}
