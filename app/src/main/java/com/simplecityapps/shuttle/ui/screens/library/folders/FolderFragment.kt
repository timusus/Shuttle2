package com.simplecityapps.shuttle.ui.screens.library.folders

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.simplecityapps.adapter.RecyclerAdapter
import com.simplecityapps.mediaprovider.model.Song
import com.simplecityapps.shuttle.MainActivity
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_folders.*


class FolderFragment : Fragment() {

    private val adapter = RecyclerAdapter()

    private val compositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(com.simplecityapps.shuttle.R.layout.fragment_folders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()

        // Todo: Inject Repository
        compositeDisposable.add(
            (activity as MainActivity).songsRepository.getSongs()
                .map { songs ->

                    val root: Tree<Node<*>> = Tree(FolderNode("/"))
                    var currentTree = root

                    songs.forEach { song ->
                        val parts = song.path.split("/")
                        parts.forEachIndexed { index, part ->
                            currentTree = if (index == parts.size - 1) {
                                currentTree.addChild(LeafNode(song, part))
                            } else {
                                currentTree.addChild(FolderNode(part))
                            }
                        }
                        currentTree = root
                    }
                    root
                }
                .subscribe(
                    { root ->
                        adapter.setData(root.children.map { path -> FolderBinder(path.data.toString()) })

                        // Logs all children of the 'root' Tree
//                        traverse(root)
                    },
                    { error -> Log.e(MainActivity.TAG, error.toString()) })
        )
    }

    private fun traverse(tree: Tree<Node<*>>, depth: Int = 0) {

        tree.children.forEach {

            var string = ""
            (0 until depth).forEach {
                string += "\t"
            }

            Log.i(TAG, "$string${it.data}")

            traverse(it, depth + 1)
        }

    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    companion object {

        const val TAG = "FolderFragment"

        fun newInstance() = FolderFragment()
    }
}

interface Node<T> {

    val data: T
}

data class FolderNode(override val data: String) : Node<String> {

    override fun toString(): String {
        return "$data/"
    }
}

data class LeafNode(override val data: Song, val fileName: String) : Node<Song> {

    override fun toString(): String {
        return fileName
    }
}

class Tree<T>(val data: T) {

    val children = LinkedHashSet<Tree<T>>()

    fun addChild(data: T): Tree<T> {
        for (child in children) {
            if (child.data == data) {
                return child
            }
        }

        val child = Tree(data)
        children.add(child)
        return child
    }
}