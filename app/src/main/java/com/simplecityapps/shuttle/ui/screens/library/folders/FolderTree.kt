package com.simplecityapps.shuttle.ui.screens.library.folders

import android.net.Uri
import com.simplecityappds.saf.SafDirectoryHelper
import com.simplecityappds.saf.Trie
import com.simplecityapps.mediaprovider.model.Song
import java.net.URLDecoder

open class FileNode(
    override val uri: Uri,
    override val displayName: String,
    val song: Song,
    val parent: FileNodeTree
) : SafDirectoryHelper.FileNode {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileNode

        if (uri != other.uri) return false

        return true
    }

    override fun hashCode(): Int {
        return uri.hashCode()
    }
}

class FileNodeTree(
    override val uri: Uri,
    override val displayName: String
) : Trie<FileNodeTree, FileNode>, SafDirectoryHelper.FileNode {

    override val treeNodes: LinkedHashSet<FileNodeTree> = linkedSetOf()
    override val leafNodes: LinkedHashSet<FileNode> = linkedSetOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileNodeTree

        if (uri != other.uri) return false

        return true
    }

    override fun hashCode(): Int {
        return uri.hashCode()
    }

    override fun toString(): String {
        return "FileNodeTree(uri=$uri, displayName='$displayName')"
    }
}

fun FileNodeTree.find(uri: Uri): FileNodeTree? {

    val treePath = URLDecoder.decode(this.uri.toString(), Charsets.UTF_8.name())
    val searchPath = URLDecoder.decode(uri.toString(), Charsets.UTF_8.name())

    if (treePath == searchPath) {
        return this
    }

    treeNodes.forEach {
        val tree = it.find(uri)
        if (tree != null) {
            return tree
        }
    }

    return null
}