package com.simplecityapps.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber

object SafDirectoryHelper {
    /**
     * Traverses the contents of [rootUri], building a [DocumentNodeTree] (Trie) representing the directory structure.
     *
     * Leaves are represented by [FileNode], and only those whose mime type starts with 'audio' are included.
     *
     * This task is resource intensive. Should be called from a background thread.
     */
    fun buildFolderNodeTree(
        contentResolver: ContentResolver,
        rootUri: Uri
    ): Flow<TreeStatus> = flow {
        try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, DocumentsContract.getTreeDocumentId(rootUri))
            retrieveDocumentNodes(contentResolver, docUri, rootUri).firstOrNull()?.let { rootDocumentNode ->
                val tree = DocumentNodeTree(docUri, rootUri, rootDocumentNode.documentId, rootDocumentNode.displayName, rootDocumentNode.mimeType)
                emit(TreeStatus.Progress(tree))
                traverseDocumentNodes(tree, contentResolver, rootUri)
                emit(TreeStatus.Complete(tree))
            }
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to build folder tree ($rootUri)")
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun traverseDocumentNodes(
        parent: DocumentNodeTree,
        contentResolver: ContentResolver,
        rootUri: Uri
    ) {
        val documentNodes = retrieveDocumentNodes(contentResolver, DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, parent.documentId), rootUri)
        for (documentNode in documentNodes) {
            when (documentNode) {
                is DocumentNodeTree -> traverseDocumentNodes(parent.addTreeNode(documentNode), contentResolver, rootUri)
                else -> {
                    if (documentNode.mimeType.startsWith("audio")) {
                        // Add files with mimetype "audio/*"
                        parent.addLeafNode(documentNode)
                        continue
                    }

                    if (arrayOf("mp3", "3gp", "mp4", "m4a", "m4b", "aac", "ts", "flac", "mid", "xmf", "mxmf", "midi", "rtttl", "rtx", "ota", "imy", "ogg", "mkv", "wav", "opus", "m3u", "m3u8")
                            .contains(documentNode.ext)
                    ) {
                        // Add files with audio-related extensions
                        parent.addLeafNode(documentNode)
                        continue
                    }
                }
            }
        }
    }

    /**
     * Builds a list of [DocumentNode] from the passed in [Uri].
     *
     * This involves a content resolver query, and should be called from a background thread.
     */
    private suspend fun retrieveDocumentNodes(
        contentResolver: ContentResolver,
        uri: Uri,
        rootUri: Uri
    ): List<DocumentNode> = withContext(Dispatchers.IO) {
        val documentNodes = mutableListOf<DocumentNode>()
        try {
            contentResolver.query(
                uri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null,
                null,
                null
            ).use { cursor ->
                cursor?.let {
                    while (cursor.moveToNext()) {
                        val mimeType = cursor.getString(2)
                        val documentId = cursor.getString(0)
                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            documentNodes.add(
                                DocumentNodeTree(
                                    uri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId),
                                    rootUri = rootUri,
                                    documentId = documentId,
                                    displayName = cursor.getString(1),
                                    mimeType = mimeType
                                )
                            )
                        } else {
                            documentNodes.add(
                                DocumentNode(
                                    uri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId),
                                    documentId = documentId,
                                    displayName = cursor.getString(1),
                                    mimeType = mimeType
                                )
                            )
                        }
                    }
                } ?: Timber.e("Failed to iterate cursor (null)")
            }
        } catch (e: SecurityException) {
            Timber.e("Failed to retrieve document node for uri: $uri")
        }
        documentNodes
    }

    sealed class TreeStatus(val tree: DocumentNodeTree) {
        class Progress(tree: DocumentNodeTree) : TreeStatus(tree)

        class Complete(tree: DocumentNodeTree) : TreeStatus(tree)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TreeStatus

            if (tree != other.tree) return false

            return true
        }

        override fun hashCode(): Int = tree.hashCode()
    }
}
