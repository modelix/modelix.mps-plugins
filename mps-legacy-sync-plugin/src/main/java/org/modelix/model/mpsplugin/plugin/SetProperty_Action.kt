package org.modelix.model.mpsplugin.plugin

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import jetbrains.mps.workbench.action.ActionAccess
import jetbrains.mps.workbench.action.BaseAction
import org.jetbrains.mps.openapi.language.SProperty
import org.modelix.model.api.INode
import org.modelix.model.area.PArea
import org.modelix.model.mpsplugin.history.CloudNodeTreeNode
import org.modelix.model.mpsplugin.history.CloudNodeTreeNodeBinding
import javax.swing.Icon
import javax.swing.tree.TreeNode

/*Generated by MPS */
class SetProperty_Action(private val node: INode, private val role: SProperty) :
    BaseAction("Set Property '...'", "", ICON) {
    init {
        setIsAlwaysVisible(false)
        actionAccess = ActionAccess.NONE
    }

    override fun isDumbAware(): Boolean {
        return true
    }

    override fun isApplicable(event: AnActionEvent, _params: Map<String, Any>): Boolean {
        event.presentation.text = "Set Property '" + role.name + "' (" + role.owner.name + ")"
        return true
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
        val nodeTreeNode: CloudNodeTreeNode = (event.getData(MPSCommonDataKeys.TREE_NODE) as CloudNodeTreeNode?)!!
        val currentValue: String? = CloudNodeTreeNodeBinding.getTreeInRepository(nodeTreeNode).computeRead({
            nodeTreeNode.node.getPropertyValue(
                role.name,
            )
        })
        val value: String? = Messages.showInputDialog(
            event.getData(CommonDataKeys.PROJECT),
            "Value",
            "Set Property '" + role.name + "'",
            null,
            currentValue,
            object : InputValidator {
                override fun checkInput(s: String): Boolean {
                    // TODO perhaps look into the type of the property to authorize it or not
                    return true
                }

                override fun canClose(s: String): Boolean {
                    return true
                }
            },
        )
        if (value == null) {
            return
        }
        PArea(nodeTreeNode.branch).executeWrite<Unit>({
            node.setPropertyValue(role.name, value)
        })
    }

    override fun getActionId(): String {
        val res: StringBuilder = StringBuilder()
        res.append(super.getActionId())
        res.append("#")
        res.append(node_State(node))
        res.append("!")
        res.append(role_State(role))
        res.append("!")
        return res.toString()
    }

    companion object {
        private val ICON: Icon? = null
        fun node_State(`object`: INode): String {
            return `object`.toString()
        }

        fun role_State(`object`: SProperty): String {
            return `object`.name
        }
    }
}
