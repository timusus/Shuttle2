package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.lifecycle.ViewModel
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.mediaprovider.repository.SongRepository
import io.reactivex.Single
import javax.inject.Inject

class FolderViewModel @Inject constructor(private val songsRepository: SongRepository) : ViewModel() {

    private lateinit var single: Single<Tree<Node<Song>>>

    fun getRoot(): Single<Tree<Node<Song>>> {
        if (!::single.isInitialized) {
            single = songsRepository
                .getSongs()
                .first(emptyList())
                .map { songs ->

                    val root: Tree<Node<Song>> = Tree(Node("", ""))
                    var currentTree = root

                    songs.forEach { song ->
                        val parts = song.path.split("/")
                        parts.forEachIndexed { index, part ->
                            var path = currentTree.node.path
                            if (path != "/") {
                                path += "/"
                            }
                            currentTree = if (index == parts.size - 1) {
                                currentTree.addChild(Node(path, part, song))
                            } else {
                                currentTree.addChild(Node(path + part, part))
                            }
                        }
                        currentTree = root
                    }
                    root
                }
                .cache()
        }

        return single
    }
}