package com.simplecityappds.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.Serializable

object SafDirectoryHelper {

    const val TAG = "SafDirectoryHelper"

    interface DocumentNode : Node, Serializable {
        val uri: Uri
        val documentId: String
        val displayName: String
        val mimeType: String
    }

    data class FileNode(
        override val uri: Uri,
        override val documentId: String,
        override val displayName: String,
        override val mimeType: String
    ) : DocumentNode

    data class FolderNodeTree(
        override val uri: Uri,
        val rootUri: Uri,
        override val documentId: String,
        override val displayName: String,
        override val mimeType: String
    ) : Trie<FolderNodeTree, FileNode>,
        DocumentNode {

        override val treeNodes: LinkedHashSet<TreeNode> = linkedSetOf()
        override val leafNodes: LinkedHashSet<Node> = linkedSetOf()
    }

    /**
     * Traverses the contents of [treeUri], building a [FolderNodeTree] (Trie) representing the directory structure.
     *
     * Leaves are represented by [FileNode], and only those whose mime type starts with 'audio' are included.
     *
     * This task is resource intensive. Should be called from a background thread.
     */
    fun buildFolderNodeTree(
        contentResolver: ContentResolver,
        treeUri: Uri,
        callback: ((tree: FolderNodeTree?, traversalComplete: Boolean) -> Unit)? = null
    ): FolderNodeTree? {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        retrieveDocumentNodes(contentResolver, docUri, treeUri).firstOrNull()?.let { rootDocumentNode ->
            val tree = FolderNodeTree(docUri, treeUri, rootDocumentNode.documentId, rootDocumentNode.displayName, rootDocumentNode.mimeType)
            callback?.invoke(tree, false)

            fun traverseDocumentNodes(parent: FolderNodeTree) {
                val documentNodes = retrieveDocumentNodes(contentResolver, DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.documentId), treeUri)
                for (documentNode in documentNodes) {
                    when (documentNode) {
                        is FolderNodeTree -> traverseDocumentNodes(parent.addTreeNode(documentNode))
                        is FileNode -> {
                            if (documentNode.mimeType.startsWith("audio")) {
                                parent.addLeafNode(documentNode)
                            }
                        }
                    }
                }
            }

            traverseDocumentNodes(tree)
            callback?.invoke(tree, true)
            return tree
        } ?: run {
            callback?.invoke(null, false)
            return null
        }
    }

    /**
     * Builds a list of [DocumentNode] from the passed in [Uri].
     *
     * This involves a content resolver query, and should be called from a background thread.
     */
    private fun retrieveDocumentNodes(contentResolver: ContentResolver, uri: Uri, rootUri: Uri): List<DocumentNode> {
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
                        documentNodes.add(FolderNodeTree(DocumentsContract.buildDocumentUriUsingTree(uri, documentId), rootUri, documentId, cursor.getString(1), mimeType))
                    } else {
                        documentNodes.add(FileNode(DocumentsContract.buildDocumentUriUsingTree(uri, documentId), documentId, cursor.getString(1), mimeType))
                    }
                }
            } ?: Log.e(TAG, "Failed to iterate cursor (null)")
        }
        return documentNodes
    }
}