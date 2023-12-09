package org.modelix.model.mpsplugin.plugin

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Pair
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.internal.collections.runtime.SetSequence
import jetbrains.mps.plugins.actions.GeneratedActionGroup
import jetbrains.mps.project.MPSProject
import jetbrains.mps.workbench.ActionPlace
import jetbrains.mps.workbench.action.ApplicationPlugin
import jetbrains.mps.workbench.action.BaseAction
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.util.Condition
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.lazy.unwrap
import org.modelix.model.mpsadapters.mps.SConceptAdapter
import org.modelix.model.mpsplugin.history.CloudNodeTreeNode

/*Generated by MPS */
class CloudNodeGroupSetProperty_ActionGroup(plugin: ApplicationPlugin) :
    GeneratedActionGroup("Set Property", ID, plugin) {
    private val myPlaces: Set<Pair<ActionPlace, Condition<BaseAction>?>> = SetSequence.fromSet(HashSet())

    init {
        setIsInternal(false)
        setPopup(true)
    }

    public override fun doUpdate(event: AnActionEvent) {
        removeAll()
        val project: MPSProject? = event.getData(MPSCommonDataKeys.MPS_PROJECT)
        val treeNode: CloudNodeTreeNode? =
            as_shasbg_a0a2a4(event.getData(MPSCommonDataKeys.TREE_NODE), CloudNodeTreeNode::class.java)
        if (treeNode == null) {
            return
        }
        val node: INode = treeNode.node
        if (!(node is PNodeAdapter)) {
            return
        }
        val concept: IConcept? = node.concept
        if (concept == null) {
            return
        }
        val sconcept: SAbstractConcept? = SConceptAdapter.Companion.unwrap(concept)
        val properties: Iterable<SProperty> = sconcept!!.getProperties()
        for (role: SProperty in Sequence.fromIterable(properties).sort(
            object : ISelector<SProperty, String>() {
                public override fun select(it: SProperty): String {
                    return it.getName()
                }
            },
            true,
        )) {
            this@CloudNodeGroupSetProperty_ActionGroup.addParameterizedAction(
                SetProperty_Action(node, role),
                node,
                role,
            )
        }
        for (p: Pair<ActionPlace, Condition<BaseAction>?> in myPlaces) {
            addPlace(p.first, p.second)
        }
    }

    public override fun addPlace(place: ActionPlace, cond: Condition<BaseAction>?) {
        SetSequence.fromSet(myPlaces).addElement(Pair(place, cond))
    }

    public override fun isStrict(): Boolean {
        return false
    }

    companion object {
        val ID: String = "org.modelix.model.mpsplugin.plugin.CloudNodeGroupSetProperty_ActionGroup"
        private fun <T> as_shasbg_a0a2a4(o: Any?, type: Class<T>): T? {
            return (if (type.isInstance(o)) o as T? else null)
        }
    }
}
