package com.simplecityapps.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException
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
     * Ends with [TreeStatus.Complete], or [TreeStatus.Unavailable] if any folder of the tree can't be listed (its access
     * was revoked, its volume isn't mounted): a partial tree would look like one whose files were deleted.
     *
     * A folder [skipFolder] returns true for is left out of the tree, and its contents aren't queried.
     *
     * This task is resource intensive. Should be called from a background thread.
     */
    fun buildFolderNodeTree(
        contentResolver: ContentResolver,
        rootUri: Uri,
        skipFolder: (DocumentNodeTree) -> Boolean = { false }
    ): Flow<TreeStatus> = flow {
        val tree =
            try {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, DocumentsContract.getTreeDocumentId(rootUri))
                val rootDocumentNode = retrieveDocumentNodes(contentResolver, docUri, rootUri).firstOrNull() ?: throw FileNotFoundException("No root document")
                DocumentNodeTree(docUri, rootUri, rootDocumentNode.documentId, rootDocumentNode.displayName, rootDocumentNode.mimeType).also { tree ->
                    traverseDocumentNodes(tree, contentResolver, rootUri, skipFolder)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The documents provider is another app, so anything it throws means the tree can't be read
                Timber.e(e, "Failed to build folder tree ($rootUri)")
                null
            }
        emit(tree?.let { TreeStatus.Complete(it) } ?: TreeStatus.Unavailable(rootUri))
    }.flowOn(Dispatchers.IO)

    private suspend fun traverseDocumentNodes(
        parent: DocumentNodeTree,
        contentResolver: ContentResolver,
        rootUri: Uri,
        skipFolder: (DocumentNodeTree) -> Boolean
    ) {
        val documentNodes = retrieveDocumentNodes(contentResolver, DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, parent.documentId), rootUri)
        for (documentNode in documentNodes) {
            when (documentNode) {
                is DocumentNodeTree -> if (!skipFolder(documentNode)) traverseDocumentNodes(parent.addTreeNode(documentNode), contentResolver, rootUri, skipFolder)

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
     *
     * @throws SecurityException without access to [uri]
     * @throws FileNotFoundException if the documents provider returns no cursor
     */
    private suspend fun retrieveDocumentNodes(
        contentResolver: ContentResolver,
        uri: Uri,
        rootUri: Uri
    ): List<DocumentNode> = withContext(Dispatchers.IO) {
        val documentNodes = mutableListOf<DocumentNode>()
        contentResolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
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
                                mimeType = mimeType,
                                lastModified = cursor.getLong(3),
                                size = cursor.getLong(4)
                            )
                        )
                    }
                }
            } ?: throw FileNotFoundException("No cursor for $uri")
        }
        documentNodes
    }

    sealed interface TreeStatus {
        data class Complete(val tree: DocumentNodeTree) : TreeStatus

        /** Some or all of the tree at [rootUri] couldn't be read. */
        data class Unavailable(val rootUri: Uri) : TreeStatus
    }
}
