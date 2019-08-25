package com.simplecityappds.saf

import java.io.Serializable

interface Node

interface TreeNode : Serializable {
    val treeNodes: LinkedHashSet<TreeNode>
    val leafNodes: LinkedHashSet<Node>
}

interface Trie<TN : TreeNode, N : Node> : TreeNode {

    fun addTreeNode(treeNode: TN): TN {
        treeNodes.add(treeNode)
        return treeNode
    }

    fun addLeafNode(leafNode: N) {
        leafNodes.add(leafNode)
    }

    fun getLeaves(): List<Node> {
        val leaves = leafNodes.toMutableList()

        fun traverseTree(parentTreeNode: TreeNode) {
            for (treeNode in parentTreeNode.treeNodes) {
                leaves.addAll(treeNode.leafNodes.toList())
                traverseTree(treeNode)
            }
        }
        traverseTree(this)

        return leaves
    }
}