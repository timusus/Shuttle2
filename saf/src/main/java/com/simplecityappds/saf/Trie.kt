package com.simplecityappds.saf

import java.io.Serializable

interface Node

interface TreeNode<TN, N> : Serializable {
    val treeNodes: LinkedHashSet<TN>
    val leafNodes: LinkedHashSet<N>
}

interface Trie<TN : TreeNode<TN, N>, N : Node> : TreeNode<TN, N> {

    fun addTreeNode(treeNode: TN): TN {

        treeNodes
            .firstOrNull { childTreeNode -> childTreeNode == treeNode }?.let { existingChildNode ->
            return existingChildNode
        }

        treeNodes.add(treeNode)
        return treeNode
    }

    fun addLeafNode(leafNode: N) {
        leafNodes.add(leafNode)
    }

    fun getLeaves(): List<N> {
        val leaves = leafNodes.toMutableList()

        fun traverseTree(parentTreeNode: TreeNode<TN, N>) {
            for (treeNode in parentTreeNode.treeNodes) {
                leaves.addAll(treeNode.leafNodes.toList())
                traverseTree(treeNode)
            }
        }
        traverseTree(this)

        return leaves
    }
}