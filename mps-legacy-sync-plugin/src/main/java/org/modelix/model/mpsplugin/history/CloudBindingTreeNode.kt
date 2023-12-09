package org.modelix.model.mpsplugin.history

import jetbrains.mps.ide.ui.tree.TextTreeNode
import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.IVisitor
import jetbrains.mps.internal.collections.runtime.MapSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import org.modelix.model.mpsplugin.Binding
import org.modelix.model.mpsplugin.CloudRepository
import org.modelix.model.mpsplugin.ModelServerConnection
import javax.swing.SwingUtilities
import javax.swing.tree.TreeNode

/*Generated by MPS */
class CloudBindingTreeNode(val binding: Binding?, val repositoryInModelServer: CloudRepository) : TextTreeNode(
    binding.toString()
) {
    private val bindingListener: Binding.IListener = object : Binding.IListener {
        public override fun bindingAdded(binding: Binding?) {
            updateBindingsLater()
        }

        public override fun bindingRemoved(binding: Binding?) {
            updateBindingsLater()
        }

        public override fun ownerChanged(newOwner: Binding?) {
            updateBindingsLater()
        }

        public override fun bindingActivated() {
            updateText()
            updateBindingsLater()
        }

        public override fun bindingDeactivated() {
            updateText()
            updateBindingsLater()
        }
    }

    init {
        updateBindings()
    }

    override fun onAdd() {
        super.onAdd()
        binding!!.addListener(bindingListener)
    }

    override fun onRemove() {
        super.onRemove()
        binding!!.removeListener(bindingListener)
    }

    val modelServer: ModelServerConnection?
        get() {
            return repositoryInModelServer.modelServer
        }

    fun updateBindingsLater() {
        SwingUtilities.invokeLater(object : Runnable {
            public override fun run() {
                updateBindings()
            }
        })
    }

    fun updateText() {
        setText(binding.toString() + ((if (binding!!.isActive) "" else " [disabled]")))
    }

    fun updateBindings() {
        val existing: Map<Binding?, CloudBindingTreeNode> = MapSequence.fromMap(HashMap())
        Sequence.fromIterable<TreeNode?>(TreeModelUtil.getChildren(this)).ofType<CloudBindingTreeNode>(
            CloudBindingTreeNode::class.java
        ).visitAll(object : IVisitor<CloudBindingTreeNode>() {
            public override fun visit(it: CloudBindingTreeNode) {
                MapSequence.fromMap(existing).put(it.binding, it)
            }
        })
        TreeModelUtil.setChildren(this, Sequence.fromIterable<Binding?>(
            binding!!.getOwnedBindings()
        ).select<CloudBindingTreeNode>(object : ISelector<Binding?, CloudBindingTreeNode?>() {
            public override fun select(it: Binding?): CloudBindingTreeNode? {
                return (if (MapSequence.fromMap<Binding?, CloudBindingTreeNode>(existing)
                        .containsKey(it)
                ) MapSequence.fromMap<Binding?, CloudBindingTreeNode>(existing).get(it) else CloudBindingTreeNode(
                    it,
                    repositoryInModelServer
                ))
            }
        }).ofType<TreeNode>(TreeNode::class.java)
        )
    }
}