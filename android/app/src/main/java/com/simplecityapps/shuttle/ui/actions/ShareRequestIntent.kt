package com.simplecityapps.shuttle.ui.actions

import android.content.ClipData
import android.content.Intent
import androidx.core.net.toUri

/** An [Intent.ACTION_SEND] (or `SEND_MULTIPLE`) intent for this request, to wrap in [Intent.createChooser]. */
fun ShareRequest.toIntent(): Intent {
    val uris = streams.map { it.toUri() }
    val intent = if (uris.size > 1) {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    } else {
        Intent(Intent.ACTION_SEND).apply { uris.firstOrNull()?.let { putExtra(Intent.EXTRA_STREAM, it) } }
    }
    return intent.apply {
        type = mimeType
        putExtra(Intent.EXTRA_TEXT, text)
        if (uris.isNotEmpty()) {
            clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
