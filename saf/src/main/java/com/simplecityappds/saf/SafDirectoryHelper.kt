package com.simplecityappds.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import java.io.Serializable

object SafDirectoryHelper {

    const val TAG = "SafDirectoryHelper"

    interface FileNode : Node, Serializable {
        val uri: Uri
        val displayName: String
    }

    open class DocumentNode(
        override val uri: Uri,
        open val documentId: String,
        override val displayName: String,
        open val mimeType: String
    ) : FileNode {
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

    class DocumentNodeTree(
        override val uri: Uri,
        val rootUri: Uri,
        override val documentId: String,
        override val displayName: String,
        override val mimeType: String
    ) : Trie<DocumentNodeTree, FileNode>,
        DocumentNode(uri, documentId, displayName, mimeType) {

        override val treeNodes: LinkedHashSet<DocumentNodeTree> = linkedSetOf()
        override val leafNodes: LinkedHashSet<FileNode> = linkedSetOf()

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

    /**
     * Traverses the contents of [treeUri], building a [DocumentNodeTree] (Trie) representing the directory structure.
     *
     * Leaves are represented by [FileNode], and only those whose mime type starts with 'audio' are included.
     *
     * This task is resource intensive. Should be called from a background thread.
     */
    suspend fun buildFolderNodeTree(
        contentResolver: ContentResolver,
        treeUri: Uri
    ): Flow<DocumentNodeTree> {
        return flow {
            try {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
                retrieveDocumentNodes(contentResolver, docUri, treeUri).firstOrNull()?.let { rootDocumentNode ->
                    val tree = DocumentNodeTree(docUri, treeUri, rootDocumentNode.documentId, rootDocumentNode.displayName, rootDocumentNode.mimeType)

                    emit(tree)

                    fun traverseDocumentNodes(parent: DocumentNodeTree) {
                        val documentNodes = retrieveDocumentNodes(contentResolver, DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.documentId), treeUri)
                        for (documentNode in documentNodes) {
                            when (documentNode) {
                                is DocumentNodeTree -> traverseDocumentNodes(parent.addTreeNode(documentNode))
                                else -> {
                                    if (documentNode.mimeType.startsWith("audio") ||
                                        arrayOf("mp3", "3gp", "mp4", "m4a", "m4b", "aac", "ts", "flac", "mid", "xmf", "mxmf", "midi", "rtttl", "rtx", "ota", "imy", "ogg", "mkv", "wav")
                                            .contains(documentNode.displayName.substringAfterLast('.'))
                                    ) {
                                        parent.addLeafNode(documentNode)
                                    }
                                }
                            }
                        }
                    }

                    traverseDocumentNodes(tree)

                    emit(tree)
                }
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to build folder tree ($treeUri)")
            }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Builds a list of [DocumentNode] from the passed in [Uri].
     *
     * This involves a content resolver query, and should be called from a background thread.
     */
    private fun retrieveDocumentNodes(contentResolver: ContentResolver, uri: Uri, rootUri: Uri): List<SafDirectoryHelper.DocumentNode> {
        val documentNodes = mutableListOf<DocumentNode>()

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
            cursor?.let { cursor ->
                while (cursor.moveToNext()) {
                    val mimeType = cursor.getString(2)
                    val documentId = cursor.getString(0)
                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        documentNodes.add(DocumentNodeTree(DocumentsContract.buildDocumentUriUsingTree(uri, documentId), rootUri, documentId, cursor.getString(1), mimeType))
                    } else {
                        documentNodes.add(DocumentNode(DocumentsContract.buildDocumentUriUsingTree(uri, documentId), documentId, cursor.getString(1), mimeType))
                    }
                }
            } ?: Log.e(TAG, "Failed to iterate cursor (null)")
        }
        return documentNodes
    }
}