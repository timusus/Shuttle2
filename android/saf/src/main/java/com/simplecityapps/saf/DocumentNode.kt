package com.simplecityapps.saf

import android.net.Uri

open class DocumentNode(
    override val uri: Uri,
    open val documentId: String,
    override val displayName: String,
    open val mimeType: String
) : FileNode {
    val ext by lazy { displayName.substringAfterLast('.') }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DocumentNode

        if (uri != other.uri) return false
        if (documentId != other.documentId) return false
        if (displayName != other.displayName) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
