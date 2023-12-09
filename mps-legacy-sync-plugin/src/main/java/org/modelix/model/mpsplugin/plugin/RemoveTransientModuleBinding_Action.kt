package org.modelix.model.mpsplugin.plugin

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import jetbrains.mps.workbench.action.BaseAction
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsplugin.CloudRepository
import org.modelix.model.mpsplugin.ModelServerConnection
import org.modelix.model.mpsplugin.TransientModuleBinding
import org.modelix.model.mpsplugin.history.CloudNodeTreeNode
import org.modelix.model.mpsplugin.history.ModelServerTreeNode
import org.modelix.model.mpsplugin.history.RepositoryTreeNode
import org.modelix.model.mpsplugin.history.TreeNodeBinding
import org.modelix.model.mpsplugin.history.TreeNodeClassification
import javax.swing.Icon
import javax.swing.tree.TreeNode

/*Generated by MPS */
class RemoveTransientModuleBinding_Action : BaseAction("Unbind from Transient Module", "", ICON) {
    init {
        setIsAlwaysVisible(false)
        setExecuteOutsideCommand(true)
    }

    override fun isDumbAware(): Boolean {
        return true
    }

    override fun isApplicable(event: AnActionEvent, _params: Map<String, Any>): Boolean {
        return TreeNodeClassification.isModuleNode(event.getData(MPSCommonDataKeys.TREE_NODE)) && TreeNodeBinding.isBoundAsModule(
            event.getData(MPSCommonDataKeys.TREE_NODE),
        )
    }

    public override fun doUpdate(event: AnActionEvent, _params: Map<String, Any>) {
        setEnabledState(event.presentation, isApplicable(event, _params))
    }

    override fun collectActionData(event: AnActionEvent, _params: Map<String, Any>): Boolean {
        if (!(super.collectActionData(event, _params))) {
            return false
        }
        run({
            val p: Project? = event.getData(CommonDataKeys.PROJECT)
            if (p == null) {
                return false
            }
        })
        run({
            val p: TreeNode? = event.getData(MPSCommonDataKeys.TREE_NODE)
            if (p == null) {
                return false
            }
        })
        return true
    }

    public override fun doExecute(event: AnActionEvent, _params: Map<String, Any>) {
        removeTransientModuleBinding(
            event.getData(MPSCommonDataKeys.TREE_NODE),
            event.getData(CommonDataKeys.PROJECT),
            TreeNodeBinding.getTransientModuleBinding(event.getData(MPSCommonDataKeys.TREE_NODE)),
            event,
        )
    }

    fun removeTransientModuleBinding(
        treeNode: TreeNode?,
        mpsProject: Project?,
        transientModuleBinding: TransientModuleBinding?,
        event: AnActionEvent?,
    ) {
        val nodeTreeNode: CloudNodeTreeNode? = treeNode as CloudNodeTreeNode?
        val repositoryId: RepositoryId = nodeTreeNode!!.getAncestor(RepositoryTreeNode::class.java).repositoryId
        val modelServerConnection: ModelServerConnection = nodeTreeNode.getAncestor(
            ModelServerTreeNode::class.java,
        ).modelServer
        modelServerConnection!!.removeBinding(transientModuleBinding)
        val treeInRepository: CloudRepository = CloudRepository(modelServerConnection, repositoryId)
        PersistedBindingConfiguration.Companion.getInstance(mpsProject)!!
            .removeTransientBoundModule(treeInRepository, nodeTreeNode)
    }

    companion object {
        private val ICON: Icon? = null
    }
}
