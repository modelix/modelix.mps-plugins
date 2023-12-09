package org.modelix.model.mpsplugin.plugin

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.Pair
import jetbrains.mps.ide.actions.MPSCommonDataKeys
import jetbrains.mps.internal.collections.runtime.CollectionSequence
import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.IWhereFilter
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.internal.collections.runtime.SetSequence
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SConceptOperations
import jetbrains.mps.plugins.actions.GeneratedActionGroup
import jetbrains.mps.project.MPSProject
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.language.LanguageRegistry
import jetbrains.mps.workbench.ActionPlace
import jetbrains.mps.workbench.action.ApplicationPlugin
import jetbrains.mps.workbench.action.BaseAction
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.util.Condition
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.lazy.unwrap
import org.modelix.model.mpsadapters.mps.SConceptAdapter
import org.modelix.model.mpsplugin.ModelixNotifications
import org.modelix.model.mpsplugin.history.CloudNodeTreeNode
import java.util.Objects

/*Generated by MPS */
class CloudNodeGroupAddChild_ActionGroup(plugin: ApplicationPlugin) :
    GeneratedActionGroup("Add Child Node", ID, plugin) {
    private val myPlaces: Set<Pair<ActionPlace, Condition<BaseAction>?>> = SetSequence.fromSet(HashSet())

    init {
        setIsInternal(false)
        setPopup(true)
    }

    public override fun doUpdate(event: AnActionEvent) {
        removeAll()
        val project: MPSProject? = event.getData(MPSCommonDataKeys.MPS_PROJECT)
        val treeNode: CloudNodeTreeNode? =
            as_pvys46_a0a2a4(event.getData(MPSCommonDataKeys.TREE_NODE), CloudNodeTreeNode::class.java)
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
        if (sconcept == null) {
            ModelixNotifications.notifyError(
                "Unable to unwrap concept",
                "We were unable to unwrap concept " + concept.getLongName() + " (" + concept.javaClass.getCanonicalName() + ")",
                project
            )
            return
        }
        val allLanguages_: Iterable<SLanguage> =
            LanguageRegistry.getInstance(project!!.getRepository()).getAllLanguages()
        val allLanguages: Set<SLanguage> = SetSequence.fromSetWithValues(HashSet(), allLanguages_)
        for (role: SContainmentLink in CollectionSequence.fromCollection<SContainmentLink>(sconcept.getContainmentLinks())) {
            if (Objects.equals(role, LINKS.`smodelAttribute$KJ43`)) {
                continue
            }
            var subConcepts: Iterable<SAbstractConcept>? =
                SConceptOperations.getAllSubConcepts(role.getTargetConcept(), allLanguages)
            subConcepts = Sequence.fromIterable(subConcepts).where(object : IWhereFilter<SAbstractConcept>() {
                public override fun accept(it: SAbstractConcept): Boolean {
                    return !(it.isAbstract())
                }
            })
            if (Objects.equals(role, LINKS.`rootNodes$jxXY`)) {
                subConcepts = Sequence.fromIterable(subConcepts).ofType(
                    SConcept::class.java
                ).where(object : IWhereFilter<SConcept>() {
                    public override fun accept(it: SConcept): Boolean {
                        return it.isRootable()
                    }
                }).ofType(SAbstractConcept::class.java)
            }
            subConcepts = Sequence.fromIterable(subConcepts).sort(object : ISelector<SAbstractConcept, String>() {
                public override fun select(it: SAbstractConcept): String {
                    return it.getLanguage().getQualifiedName()
                }
            }, true).alsoSort(object : ISelector<SAbstractConcept, String>() {
                public override fun select(it: SAbstractConcept): String {
                    return it.getName()
                }
            }, true)
            for (subconcept: SAbstractConcept? in Sequence.fromIterable(subConcepts)) {
                this@CloudNodeGroupAddChild_ActionGroup.addParameterizedAction(
                    AddChildNode_Action(
                        node,
                        subconcept,
                        role
                    ), node, subconcept, role
                )
            }
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

    private object LINKS {
        /*package*/
        val `smodelAttribute$KJ43`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            -0x3154ae6ada15b0deL,
            -0x646defc46a3573f4L,
            0x10802efe25aL,
            0x47bf8397520e5942L,
            "smodelAttribute"
        )

        /*package*/
        val `rootNodes$jxXY`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x69652614fd1c50cL,
            0x69652614fd1c514L,
            "rootNodes"
        )
    }

    companion object {
        val ID: String = "org.modelix.model.mpsplugin.plugin.CloudNodeGroupAddChild_ActionGroup"
        private fun <T> as_pvys46_a0a2a4(o: Any?, type: Class<T>): T? {
            return (if (type.isInstance(o)) o as T? else null)
        }
    }
}