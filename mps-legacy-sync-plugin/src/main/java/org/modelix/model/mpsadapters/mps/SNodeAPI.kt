package org.modelix.model.mpsadapters.mps

import jetbrains.mps.internal.collections.runtime.CollectionSequence
import jetbrains.mps.internal.collections.runtime.IMapping
import jetbrains.mps.internal.collections.runtime.MapSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SConceptOperations
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SReference
import org.modelix.model.api.INode
import org.modelix.model.api.deepUnwrapNode

/*Generated by MPS */
object SNodeAPI {
    fun clearChildren(parent: SNode, role: SContainmentLink?) {
        while (parent.getChildren(role).iterator().hasNext()) {
            parent.removeChild(parent.getChildren(role).iterator().next())
        }
    }

    fun replaceWithNewChild(parent: SNode, role: SContainmentLink, childConcept: SAbstractConcept?): SNode {
        clearChildren(parent, role)
        return addNewChild(parent, role, 0, childConcept)
    }

    fun <T : SNode> replaceWithCopy(sourceNode: T, targetParent: SNode, targetRole: SContainmentLink): T {
        clearChildren(targetParent, targetRole)
        return copyTo(sourceNode, targetParent, targetRole, 0)
    }

    fun addNewChild(parent: SNode, role: SContainmentLink, childConcept: SAbstractConcept?): SNode {
        return addNewChild(parent, role, -1, childConcept)
    }

    fun addNewChild(parent: SNode, role: SContainmentLink, index: Int): SNode {
        return addNewChild(parent, role, index, role.getTargetConcept())
    }

    fun addNewChild(
        parent: SNode,
        role: SContainmentLink,
        index: Int = -1,
        childConcept: SAbstractConcept? = role.getTargetConcept(),
    ): SNode {
        val newChild: INode = SNodeToNodeAdapter.Companion.wrap(parent)!!
            .addNewChild(role.getName(), index, SConceptAdapter.Companion.wrap(childConcept))
        if (newChild == null) {
            throw RuntimeException("addNewChild has to return the created child node")
        }
        return NodeToSNodeAdapter.wrap(newChild)
    }

    fun moveChild(newParent: SNode?, newRole: SContainmentLink, newIndex: Int, child: SNode?) {
        SNodeToNodeAdapter.Companion.wrap(newParent)!!
            .moveChild(newRole.getName(), newIndex, (SNodeToNodeAdapter.Companion.wrap(child))!!)
    }

    fun <T : SNode> copyAsMPSNode(sourceNode: T): T {
        return copyRootNode(
            sourceNode,
            SConceptOperations.createNewNode(
                SNodeOperations.asInstanceConcept(
                    SNodeOperations.getConcept(sourceNode),
                ),
            ) as T,
        )
    }

    fun <T : SNode> copyTo(sourceNode: T, targetParent: SNode, targetRole: SContainmentLink, targetIndex: Int): T {
        val concept: SAbstractConcept = SNodeOperations.getConcept(sourceNode)
        val copy: SNode = addNewChild(targetParent, targetRole, targetIndex, concept)
        return copyRootNode(sourceNode, (copy as T))
    }

    internal fun <T : SNode> copyRootNode(sourceNode: T, copy: T): T {
        val copiedNodes: Map<SNode, SNode> = MapSequence.fromMap(HashMap())
        MapSequence.fromMap(copiedNodes).put(sourceNode, copy)
        copyProperties(sourceNode, copy)
        copyChildren(sourceNode, copy, copiedNodes)
        resolveReferences(copiedNodes)
        return copy
    }

    private fun copyTo(
        sourceNode: SNode,
        targetParent: SNode,
        targetRole: SContainmentLink,
        targetIndex: Int,
        copiedNodes: Map<SNode, SNode>,
    ): SNode {
        val concept: SConcept = sourceNode.getConcept()
        val copy: SNode = addNewChild(targetParent, targetRole, targetIndex, concept)
        MapSequence.fromMap(copiedNodes).put(sourceNode, copy)
        copyProperties(sourceNode, copy)
        copyChildren(sourceNode, copy, copiedNodes)
        return copy
    }

    private fun copyProperties(source: SNode, target: SNode?) {
        for (property: SProperty in CollectionSequence.fromCollection(source.getConcept().getProperties())) {
            val value: String? = source.getProperty(property)
            if (value != null) {
                target!!.setProperty(property, value)
            }
        }
    }

    private fun copyChildren(source: SNode, target: SNode, copiedNodes: Map<SNode, SNode>) {
        for (link: SContainmentLink in CollectionSequence.fromCollection(source.getConcept().getContainmentLinks())) {
            for (child: SNode in Sequence.fromIterable(source.getChildren(link))) {
                copyTo(child, target, link, -1, copiedNodes)
            }
        }
    }

    private fun resolveReferences(copiedNodes: Map<SNode, SNode?>) {
        for (entry: IMapping<SNode, SNode?> in MapSequence.fromMap(copiedNodes)) {
            for (link: SReferenceLink in CollectionSequence.fromCollection(
                entry.key().getConcept().getReferenceLinks(),
            )) {
                val ref: SReference? = entry.key().getReference(link)
                if (ref == null) {
                    continue
                }
                val originalTarget: SNode? = ref.getTargetNode()
                if (originalTarget == null) {
                    continue
                }
                val copiedTarget: SNode? = MapSequence.fromMap(copiedNodes).get(originalTarget)
                entry.value()!!.setReferenceTarget(link, (if (copiedTarget != null) copiedTarget else originalTarget))
            }
        }
    }

    fun uniqueString(node: SNode?): String {
        var node: SNode? = node
        node = NodeToSNodeAdapter.wrap(SNodeToNodeAdapter.Companion.wrap(node))
        if (node is jetbrains.mps.smodel.SNode) {
            return node.getReference().toString()
        }
        throw RuntimeException("Cannot calculate unique string for " + node)
    }

    fun getOriginalModel(node: SNode?): SModel? {
        return check_jvh1te_a0a33(getOriginalNode(node))
    }

    fun getOriginalNode(node: SNode?): SNode? {
        if (node == null) {
            return null
        }
        return as_jvh1te_a0b0jb<jetbrains.mps.smodel.SNode>(
            NodeToSNodeAdapter.wrap(deepUnwrapNode((SNodeToNodeAdapter.Companion.wrap(node))!!)),
            jetbrains.mps.smodel.SNode::class.java,
        )
    }

    fun tryGetUnwrappedNode(node: SNode?): SNode {
        val unwrapped: SNode? = getOriginalNode(node)
        return (if (unwrapped != null) unwrapped else (node)!!)
    }

    fun runRead(snode: SNode?, r: Runnable) {
        SNodeToNodeAdapter.Companion.wrap(snode)!!.getArea().executeRead<Unit>({
            r.run()
            Unit
        })
    }

    fun runWrite(snode: SNode?, r: Runnable) {
        SNodeToNodeAdapter.Companion.wrap(snode)!!.getArea().executeWrite<Unit>({
            r.run()
            Unit
        })
    }

    fun isValid(snode: SNode?): Boolean {
        return (if (snode == null) true else SNodeToNodeAdapter.Companion.wrap(snode)!!.isValid)
    }

    private fun check_jvh1te_a0a33(checkedDotOperand: SNode?): SModel? {
        if (null != checkedDotOperand) {
            return checkedDotOperand.getModel()
        }
        return null
    }

    private fun <T> as_jvh1te_a0b0jb(o: Any?, type: Class<T>): T? {
        return (if (type.isInstance(o)) o as T? else null)
    }
}
