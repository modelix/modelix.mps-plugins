package org.modelix.model.mpsplugin.history

import jetbrains.mps.baseLanguage.closures.runtime.Wrappers
import jetbrains.mps.internal.collections.runtime.IVisitor
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SProperty
import org.modelix.model.api.INode
import org.modelix.model.area.PArea
import org.modelix.model.mpsplugin.CloudRepository
import org.modelix.model.mpsplugin.ModelServerNavigation
import javax.swing.tree.TreeNode

/*Generated by MPS */
object TreeNodeAccess {
    fun getName(_this: TreeNode?): String? {
        val nodeTreeNode: CloudNodeTreeNode = ((as_ps3aya_a0a0a1(_this, CloudNodeTreeNode::class.java))!!)
        return PArea(nodeTreeNode.branch).executeRead({
            nodeTreeNode.node.getPropertyValue(PROPS.`name$MnvL`.name)
        })
    }

    fun delete(_this: TreeNode?) {
        val nodeTreeNode: CloudNodeTreeNode = ((as_ps3aya_a0a0a2(_this, CloudNodeTreeNode::class.java))!!)
        val parent: TreeNode? = nodeTreeNode.parent
        PArea(nodeTreeNode.branch).executeWrite({
            val nodeIN: INode = nodeTreeNode.node
            val parent: INode? = nodeIN!!.parent
            if (parent == null) {
                val found: Wrappers._boolean = Wrappers._boolean(false)
                ListSequence.fromList(ModelServerNavigation.trees(nodeTreeNode.modelServer!!))
                    .visitAll(object : IVisitor<CloudRepository>() {
                        override fun visit(tree: CloudRepository) {
                            if (ListSequence.fromList(tree.repoRoots()).contains(nodeIN)) {
                                tree.deleteRoot(nodeIN)
                                found.value = true
                            }
                        }
                    })
                if (found.value) {
                    return@executeWrite
                }
                if (!(found.value)) {
                    throw RuntimeException("Unable to remove node without parent, not found as root of any tree")
                }
            }
            parent!!.removeChild((nodeIN))
        })
        if (parent == null) {
            throw RuntimeException("Cannot remove node without parent")
        }
        if (parent is CloudNodeTreeNode) {
            parent.remove(nodeTreeNode)
        } else {
            throw RuntimeException("Unable to remove child from parent " + parent + " (" + parent.javaClass + ")")
        }
    }

    private fun <T> as_ps3aya_a0a0a1(o: Any?, type: Class<T>): T? {
        return (if (type.isInstance(o)) o as T? else null)
    }

    private fun <T> as_ps3aya_a0a0a2(o: Any?, type: Class<T>): T? {
        return (if (type.isInstance(o)) o as T? else null)
    }

    private object PROPS {
        /*package*/
        val `name$MnvL`: SProperty = MetaAdapterFactory.getProperty(
            -0x3154ae6ada15b0deL,
            -0x646defc46a3573f4L,
            0x110396eaaa4L,
            0x110396ec041L,
            "name",
        )
    }
}
