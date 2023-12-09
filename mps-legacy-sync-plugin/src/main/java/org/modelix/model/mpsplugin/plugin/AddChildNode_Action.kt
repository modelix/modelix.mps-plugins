package org.modelix.model.mpsplugin.plugin

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import jetbrains.mps.baseLanguage.closures.runtime.Wrappers._T
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.workbench.action.ActionAccess
import jetbrains.mps.workbench.action.BaseAction
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter.Companion.wrap
import org.modelix.model.area.PArea
import org.modelix.model.mpsadapters.mps.SConceptAdapter
import org.modelix.model.mpsplugin.history.CloudNodeTreeNode
import javax.swing.Icon
import javax.swing.tree.TreeNode

/*Generated by MPS */
class AddChildNode_Action(
    private val parentNode: INode,
    private val childConcept: SAbstractConcept?,
    private val role: SContainmentLink,
) : BaseAction("Add new child of concept ... in role ...", "", ICON) {
    init {
        setIsAlwaysVisible(false)
        setActionAccess(ActionAccess.NONE)
    }

    public override fun isDumbAware(): Boolean {
        return true
    }

    public override fun isApplicable(event: AnActionEvent, _params: Map<String, Any>): Boolean {
        if (childConcept == null) {
            event.getPresentation().setText("To '" + role.getName())
        } else {
            event.getPresentation().setText(
                "To '" + role.getName() + "' add '" + childConcept.getLanguage()
                    .getQualifiedName() + "." + childConcept.getName() + "'",
            )
        }
        return true
    }

    public override fun doUpdate(event: AnActionEvent, _params: Map<String, Any>) {
        setEnabledState(event.getPresentation(), isApplicable(event, _params))
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
        val nodeTreeNode: CloudNodeTreeNode? = event.getData(MPSCommonDataKeys.TREE_NODE) as CloudNodeTreeNode?
        val name: _T<String?> = _T(null)
        if (childConcept!!.getProperties().contains(PROPS.`name$MnvL`)) {
            name.value = Messages.showInputDialog(
                event.getData(CommonDataKeys.PROJECT),
                "Name",
                "Add " + childConcept.getName(),
                null,
            )
            if (isEmptyString(name.value)) {
                return
            }
        }
        PArea(nodeTreeNode!!.branch).executeWrite<Unit>({
            val newModule: INode = parentNode.addNewChild(
                role.getName(),
                -1,
                SConceptAdapter.Companion.wrap(
                    childConcept,
                ),
            )
            if (isNotEmptyString(name.value)) {
                newModule.setPropertyValue(PROPS.`name$MnvL`.getName(), name.value)
            }
            Unit
        })
    }

    public override fun getActionId(): String {
        val res: StringBuilder = StringBuilder()
        res.append(super.getActionId())
        res.append("#")
        res.append(parentNode_State(parentNode))
        res.append("!")
        res.append(childConcept_State(childConcept))
        res.append("!")
        res.append(role_State(role))
        res.append("!")
        return res.toString()
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

    companion object {
        private val ICON: Icon? = null
        fun parentNode_State(`object`: INode): String {
            return `object`.toString()
        }

        fun childConcept_State(`object`: SAbstractConcept?): String {
            if (`object` == null) {
                return "null"
            }
            return `object`.getName()
        }

        fun role_State(`object`: SContainmentLink): String {
            return `object`.getName()
        }

        private fun isEmptyString(str: String?): Boolean {
            return str == null || str.isEmpty()
        }

        private fun isNotEmptyString(str: String?): Boolean {
            return str != null && str.length > 0
        }
    }
}
