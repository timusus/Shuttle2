package com.simplecityapps.shuttle.model

/**
 * A folder in the local library, as derived by [FolderTree.build].
 *
 * [path] is the folder's segments from its storage volume (see [SongFolder]); the tree's root has an empty path.
 * [songs] are the songs directly in this folder, [songCount] includes every subfolder.
 */
class FolderNode(
    val path: List<String>,
    val subfolders: List<FolderNode>,
    val songs: List<Song>,
    val songCount: Int
) {
    val name: String get() = path.lastOrNull().orEmpty()

    /** The folder at [path], or the deepest existing ancestor of it within this subtree. */
    fun nearest(path: List<String>): FolderNode {
        var node = this
        for (segment in path.drop(this.path.size)) {
            node = node.subfolders.firstOrNull { it.name == segment } ?: return node
        }
        return node
    }
}

class FolderTree(val root: FolderNode) {
    /**
     * Where browsing starts. With a single volume (the common case) the volume level is skipped, so the user
     * lands on Music, Download… rather than on a lone "Internal storage".
     */
    val displayRoot: FolderNode = root.subfolders.singleOrNull() ?: root

    val isEmpty: Boolean get() = root.songCount == 0

    companion object {
        /** Builds the folder tree for local songs. Songs from remote providers are ignored. */
        fun build(songs: List<Song>): FolderTree {
            val root = MutableFolder(emptyList())
            for (song in songs) {
                if (song.mediaProvider.remote) continue
                val location = SongFolder.locate(song.path)
                var folder = root
                for (segment in location.folder) {
                    folder = folder.subfolders.getOrPut(segment) { MutableFolder(folder.path + segment) }
                }
                folder.songs += location.fileName to song
            }
            return FolderTree(root.toFolderNode())
        }
    }

    private class MutableFolder(val path: List<String>) {
        val subfolders = mutableMapOf<String, MutableFolder>()
        val songs = mutableListOf<Pair<String, Song>>()

        fun toFolderNode(): FolderNode {
            val children = subfolders.entries
                .sortedWith(compareBy(SongFolder.segmentComparator) { it.key })
                .map { it.value.toFolderNode() }
            return FolderNode(
                path = path,
                subfolders = children,
                songs = songs.sortedWith(compareBy(SongFolder.nameComparator) { it.first }).map { it.second },
                songCount = songs.size + children.sumOf { it.songCount }
            )
        }
    }
}
