package com.simplecityapps.shuttle.ui.screens.library.folders

import java.io.Serializable

class Tree<T : Serializable>(val node: T) : Serializable {

    val children = LinkedHashSet<Tree<T>>()

    fun addChild(node: T): Tree<T> {
        for (child in children) {
            if (child.node == node) {
                return child
            }
        }

        val child = Tree(node)
        children.add(child)
        return child
    }
}

class Node<T>(val path: String, val name: String, val data: T? = null) : Serializable {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Node<*>

        if (path != other.path) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}

fun <T> Tree<Node<T>>.find(path: String): Tree<Node<T>>? {

    if (node.path == path) {
        return this
    }

    children.forEach {
        val tree = it.find(path)
        if (tree != null) {
            return tree
        }
    }

    return null
}