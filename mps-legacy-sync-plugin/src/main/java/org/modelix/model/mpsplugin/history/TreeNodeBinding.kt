package org.modelix.model.mpsplugin.history

import org.modelix.model.mpsplugin.TransientModuleBinding
import javax.swing.tree.TreeNode

/*Generated by MPS */
object TreeNodeBinding {
    fun isBoundAsModule(_this: TreeNode?): Boolean {
        val nodeTreeNode: CloudNodeTreeNode? = as_qoya4i_a0a0a1(_this, CloudNodeTreeNode::class.java)
        if (nodeTreeNode == null) {
            return false
        } else {
            return CloudNodeTreeNodeBinding.isBoundAsAModule(nodeTreeNode)
        }
    }

    fun getTransientModuleBinding(_this: TreeNode?): TransientModuleBinding? {
        val nodeTreeNode: CloudNodeTreeNode? = as_qoya4i_a0a0a2(_this, CloudNodeTreeNode::class.java)
        if (nodeTreeNode == null) {
            return null
        } else {
            return CloudNodeTreeNodeBinding.getTransientModuleBinding(nodeTreeNode)
        }
    }

    private fun <T> as_qoya4i_a0a0a1(o: Any?, type: Class<T>): T? {
        return (if (type.isInstance(o)) o as T? else null)
    }

    private fun <T> as_qoya4i_a0a0a2(o: Any?, type: Class<T>): T? {
        return (if (type.isInstance(o)) o as T? else null)
    }
}
