package org.modelix.model.mpsplugin.history

import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.ide.ui.tree.MPSTree
import jetbrains.mps.ide.ui.tree.MPSTreeNode
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.util.IterableUtil
import java.util.Objects
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath

/*Generated by MPS */
object TreeModelUtil {
    fun setChildren(parent: TreeNode, children_: Iterable<TreeNode>?) {
        val children: List<TreeNode> = Sequence.fromIterable(children_).toListSequence()
        if (Objects.equals(Sequence.fromIterable(getChildren(parent)).toListSequence(), children)) {
            return
        }
        val wasExpanded: Boolean = isExpanded(parent)
        clearChildren(parent)
        val model: DefaultTreeModel? = as_spdlqu_a0a4a0(getModel(parent), DefaultTreeModel::class.java)
        if (model != null) {
            ThreadUtils.assertEDT()
            var i: Int = 0
            for (child: TreeNode? in ListSequence.fromList<TreeNode>(children)) {
                model.insertNodeInto(child as MutableTreeNode?, parent as MutableTreeNode?, i)
                i++
            }
        } else {
            var i: Int = 0
            for (child: TreeNode? in ListSequence.fromList<TreeNode>(children)) {
                (parent as MutableTreeNode).insert(child as MutableTreeNode?, i)
                i++
            }
        }
        if (wasExpanded) {
            getTree(parent)!!.expandPath(getPath(parent))
        }
    }

    fun getChildren(parent: TreeNode): Iterable<TreeNode> {
        val result: Iterable<TreeNode> = IterableUtil.asIterable(parent.children().asIterator())
        return Sequence.fromIterable(result).ofType(
            TreeNode::class.java,
        )
    }

    fun clearChildren(parent: TreeNode) {
        val model: DefaultTreeModel? = as_spdlqu_a0a0a3(getModel(parent), DefaultTreeModel::class.java)
        if (model != null) {
            ThreadUtils.assertEDT()
            while (model.getChildCount(parent) > 0) {
                model.removeNodeFromParent(model.getChild(parent, 0) as MutableTreeNode?)
            }
        } else {
            while (parent.getChildCount() > 0) {
                (parent as MutableTreeNode).remove(0)
            }
        }
    }

    fun getModel(node: TreeNode?): TreeModel? {
        return getModel(getTree(node))
    }

    fun getModel(tree: JTree?): DefaultTreeModel? {
        if (tree == null) return null
        return tree.getModel() as DefaultTreeModel?
    }

    fun getTree(node: TreeNode?): MPSTree? {
        return (if (node is MPSTreeNode) node.getTree() else null)
    }

    fun repaint(node: TreeNode?) {
        ThreadUtils.runInUIThreadAndWait(object : Runnable {
            public override fun run() {
                check_spdlqu_a0a0a0a9(getTree(node))
            }
        })
    }

    fun setTextAndRepaint(node: MPSTreeNode, text: String?) {
        node.setText(text)
        repaint(node)
    }

    fun isExpanded(node: TreeNode): Boolean {
        return check_spdlqu_a0a31(getTree(node), node)
    }

    fun getPath(node: TreeNode): TreePath {
        if (node.getParent() == null) {
            return TreePath(node)
        } else {
            return getPath(node.getParent()).pathByAddingChild(node)
        }
    }

    private fun check_spdlqu_a0a0a0a9(checkedDotOperand: MPSTree?) {
        if (null != checkedDotOperand) {
            checkedDotOperand.repaint()
        }
    }

    private fun check_spdlqu_a0a31(checkedDotOperand: MPSTree?, node: TreeNode): Boolean {
        if (null != checkedDotOperand) {
            return checkedDotOperand.isExpanded(getPath(node))
        }
        return false
    }

    private fun <T> as_spdlqu_a0a4a0(o: Any?, type: Class<T>): T? {
        return (if (type.isInstance(o)) o as T? else null)
    }

    private fun <T> as_spdlqu_a0a0a3(o: Any?, type: Class<T>): T? {
        return (if (type.isInstance(o)) o as T? else null)
    }
}
