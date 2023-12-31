package org.modelix.model.mpsplugin.plugin

import com.intellij.openapi.actionSystem.AnActionEvent
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import jetbrains.mps.project.MPSProject
import jetbrains.mps.workbench.action.ActionAccess
import jetbrains.mps.workbench.action.BaseAction
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.mpsplugin.CloudRepository
import org.modelix.model.mpsplugin.ModelCloudImportUtils
import javax.swing.Icon

/*Generated by MPS */
class CopyAndSyncPhysicalProjectOnCloud_Action(
    private val treeInRepository: CloudRepository,
    private val cloudProject: SNode?,
) : BaseAction("Copy on Cloud & Sync", "", ICON) {
    init {
        setIsAlwaysVisible(false)
        actionAccess = ActionAccess.UNDO_PROJECT
    }

    override fun isDumbAware(): Boolean {
        return true
    }

    override fun isApplicable(event: AnActionEvent, _params: Map<String, Any>): Boolean {
        if (cloudProject == null) {
            event.presentation.text = "Copy on Cloud and Sync -> " + treeInRepository.presentation() + " as new project"
        } else {
            treeInRepository.runRead(object : Runnable {
                override fun run() {
                    event.presentation.text = "Sync to Cloud Repo " + treeInRepository.presentation() + " to project " + cloudProject
                }
            })
        }
        // TODO verify it is not already stored
        return treeInRepository.isConnected
    }

    public override fun doUpdate(event: AnActionEvent, _params: Map<String, Any>) {
        setEnabledState(event.presentation, isApplicable(event, _params))
    }

    override fun collectActionData(event: AnActionEvent, _params: Map<String, Any>): Boolean {
        if (!(super.collectActionData(event, _params))) {
            return false
        }
        run({
            val p: MPSProject? = event.getData(MPSCommonDataKeys.MPS_PROJECT)
            if (p == null) {
                return false
            }
        })
        return true
    }

    public override fun doExecute(event: AnActionEvent, _params: Map<String, Any>) {
        ModelCloudImportUtils.copyAndSyncInModelixAsEntireProject(
            treeInRepository,
            event.getData(MPSCommonDataKeys.MPS_PROJECT)!!,
            cloudProject,
        )
    }

    override fun getActionId(): String {
        val res: StringBuilder = StringBuilder()
        res.append(super.getActionId())
        res.append("#")
        res.append(treeInRepository_State(treeInRepository))
        res.append("!")
        res.append(cloudProject_State(cloudProject))
        res.append("!")
        return res.toString()
    }

    companion object {
        private val ICON: Icon? = null
        fun treeInRepository_State(`object`: CloudRepository): String {
            return `object`.presentation()
        }

        fun cloudProject_State(`object`: SNode?): String {
            return "" + `object`
        }
    }
}
