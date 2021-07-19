package com.simplecityapps.saf

import android.net.Uri

class DocumentNodeTree(
    override val uri: Uri,
    val rootUri: Uri,
    override val documentId: String,
    override val displayName: String,
    override val mimeType: String
) : Trie<DocumentNodeTree, DocumentNode>,
    DocumentNode(uri, documentId, displayName, mimeType) {

    override val treeNodes: LinkedHashSet<DocumentNodeTree> = linkedSetOf()
    override val leafNodes: LinkedHashSet<DocumentNode> = linkedSetOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as DocumentNodeTree

        if (uri != other.uri) return false
        if (rootUri != other.rootUri) return false
        if (documentId != other.documentId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + rootUri.hashCode()
        result = 31 * result + documentId.hashCode()
        return result
    }
}