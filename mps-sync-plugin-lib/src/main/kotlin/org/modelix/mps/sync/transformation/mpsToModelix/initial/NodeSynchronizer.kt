/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.mps.sync.transformation.mpsToModelix.initial

import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.IChildLink
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.NodeReference
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.data.NodeData
import org.modelix.model.mpsadapters.MPSChildLink
import org.modelix.model.mpsadapters.MPSConcept
import org.modelix.model.mpsadapters.MPSNodeReference
import org.modelix.model.mpsadapters.MPSProperty
import org.modelix.model.mpsadapters.MPSReferenceLink
import org.modelix.mps.sync.modelix.util.nodeIdAsLong
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.mps.util.getModelixId
import org.modelix.mps.sync.tasks.ContinuableSyncTask
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.exceptions.NodeAlreadySynchronizedException

/**
 * Synchronizes an [SNode] to the modelix model server. This is the class that performs the node operations on the
 * [IBranch], that will be automatically synced to the model server by modelix.
 *
 * @param serviceLocator a collector class to simplify injecting the commonly used services in the sync plugin.
 *
 * @property branch the modelix branch we are connected to.
 * @property resolvableReferences references between [SNode]s that should be resolved at a later point in time, because
 * the target modelix nodes might not be available on the [branch] yet.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class NodeSynchronizer(
    private val branch: IBranch,
    private val resolvableReferences: MutableCollection<CloudResolvableReference>? = null,
    serviceLocator: ServiceLocator,
) {

    /**
     * The lookup map (internal cache) between the MPS elements and the corresponding modelix Nodes.
     */
    private val nodeMap = serviceLocator.nodeMap

    /**
     * The task queue of the sync plugin.
     */
    private val syncQueue = serviceLocator.syncQueue

    /**
     * Adds the [node] to the model server by synchronizing all its properties, references and children recursively.
     *
     * @param node the [SNode] to be added to the model server.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
    fun addNode(node: SNode) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val parentNodeId = if (node.parent != null) {
                nodeMap[node.parent]!!
            } else {
                nodeMap[node.model]!!
            }

            val containmentLink = node.containmentLink
            val childLink = if (containmentLink == null) {
                BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes
            } else {
                MPSChildLink(containmentLink)
            }

            val mpsConcept = node.concept
            val cloudParentNode = branch.getNode(parentNodeId)

            // duplicate check
            throwExceptionIfChildExists(cloudParentNode, childLink, node)
            val cloudChildNode = cloudParentNode.addNewChild(childLink, -1, MPSConcept(mpsConcept))
            nodeMap.put(node, cloudChildNode.nodeIdAsLong())

            synchronizeNodeToCloud(mpsConcept, node, cloudChildNode)
        }

    /**
     * Synchronizes the properties, references and children of the [mpsNode] to the [cloudNode] recursively.
     *
     * In case of node references, if the reference target node is not synchronized to modelix yet, then a resolvable
     * reference will be created and put into [resolvableReferences] and will be resolved manually later (see
     * [resolveReferences]).
     *
     * @param mpsConcept the Concept of the [mpsNode].
     * @param mpsNode the [SNode] to be synchronized to the model server.
     * @param cloudNode the modelix node that represents the [mpsNode] in modelix.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
    private fun synchronizeNodeToCloud(
        mpsConcept: SConcept,
        mpsNode: SNode,
        cloudNode: INode,
    ) {
        // synchronize properties
        mpsConcept.properties.forEach {
            val modelixProperty = MPSProperty(it)
            val mpsValue = mpsNode.getProperty(it)
            cloudNode.setPropertyValue(modelixProperty, mpsValue)
        }
        /*
         * Save MPS Node ID explicitly.
         * If you change this property here, please also change in method 'throwExceptionIfChildExists', where we use
         * node.getModelixId() to check if the node already exists.
         */
        cloudNode.setPropertyValue(PropertyFromName(NodeData.ID_PROPERTY_KEY), mpsNode.getModelixId())

        // synchronize references
        mpsConcept.referenceLinks.forEach {
            val modelixReferenceLink = MPSReferenceLink(it)
            val mpsTargetNode = mpsNode.getReferenceTarget(it)
            if (resolvableReferences != null) {
                resolvableReferences.add(CloudResolvableReference(cloudNode, modelixReferenceLink, mpsTargetNode))
            } else {
                setReferenceInTheCloud(cloudNode, modelixReferenceLink, mpsTargetNode)
            }
        }

        // synchronize children
        mpsConcept.containmentLinks.forEach { containmentLink ->
            mpsNode.getChildren(containmentLink).forEach { mpsChild ->
                val childLink = MPSChildLink(containmentLink)

                // duplicate check
                throwExceptionIfChildExists(cloudNode, childLink, mpsChild)

                val mpsChildConcept = mpsChild.concept
                val cloudChildNode = cloudNode.addNewChild(childLink, -1, MPSConcept(mpsChildConcept))
                nodeMap.put(mpsChild, cloudChildNode.nodeIdAsLong())

                synchronizeNodeToCloud(mpsChildConcept, mpsChild, cloudChildNode)
            }
        }
    }

    /**
     * @param cloudParentNode the modelix node that is the parent of the [node]'s modelix representative.
     * @param childLink the containment link via which [cloudParentNode] is the parent node.
     * @param node the [SNode] for which we want to know if it is synced to modelix.
     *
     * @throws [NodeAlreadySynchronizedException] if [node] is already synced as a child node of [cloudParentNode] to
     * modelix.
     */
    @Throws(NodeAlreadySynchronizedException::class)
    private fun throwExceptionIfChildExists(cloudParentNode: INode, childLink: IChildLink, node: SNode) {
        val children = cloudParentNode.getChildren(childLink)
        val nodeExistsOnTheServer = children.any { node.getModelixId() == it.getOriginalReference() }
        val isSynchedToMps = nodeMap.isMappedToModelix(node)
        if (nodeExistsOnTheServer && !isSynchedToMps) {
            throw NodeAlreadySynchronizedException(node)
        }
    }

    /**
     * Sets the [modelixReferenceLink] of [cloudNode] to the modelix node that represents [mpsTargetNode] in modelix.
     *
     * @param cloudNode the source modelix node.
     * @param modelixReferenceLink the reference via which we want to reach the target node.
     * @param mpsTargetNode the MPS [SNode] whose corresponding modelix node will be the reference target node.
     */
    private fun setReferenceInTheCloud(cloudNode: INode, modelixReferenceLink: IReferenceLink, mpsTargetNode: SNode?) =
        if (mpsTargetNode == null) {
            cloudNode.setReferenceTarget(modelixReferenceLink, null as INode?)
        } else if (mpsTargetNode.model?.isReadOnly == true) {
            val serialized = MPSNodeReference(mpsTargetNode.reference).serialize()
            val nodeReference = NodeReference(serialized)
            cloudNode.setReferenceTarget(modelixReferenceLink, nodeReference)
        } else {
            val targetNodeId = nodeMap[mpsTargetNode]!!
            val targetNode = branch.getNode(targetNodeId)
            cloudNode.setReferenceTarget(modelixReferenceLink, targetNode)
        }

    /**
     * Sets the [property] to [newValue] in modelix on the node whose modelix node identifier is produced by
     * the [sourceNodeIdProducer].
     *
     * @param property the property whose value we want to set.
     * @param newValue the new value of the property.
     * @param sourceNodeIdProducer a producer that produces the modelix node ID of the node whose [property] we want to
     * set to [newValue].
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
    fun setProperty(property: IProperty, newValue: String, sourceNodeIdProducer: (MpsToModelixMap) -> Long) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE), SyncDirection.MPS_TO_MODELIX) {
            val nodeId = sourceNodeIdProducer.invoke(nodeMap)
            val cloudNode = branch.getNode(nodeId)
            cloudNode.setPropertyValue(property, newValue)
        }

    /**
     * Removes the modelix node, that is identified by the ID produced by the [childNodeIdProducer], from its parent
     * modelix node, that is identified by the ID produced by the [parentNodeIdProducer].
     *
     * @param parentNodeIdProducer a producer that produces the modelix node ID of the parent modelix node.
     * @param childNodeIdProducer a producer that produces the modelix node ID fo the child modelix node that has to be
     * removed from modelix.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
    fun removeNode(parentNodeIdProducer: (MpsToModelixMap) -> Long, childNodeIdProducer: (MpsToModelixMap) -> Long) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE), SyncDirection.MPS_TO_MODELIX) {
            val parentNodeId = parentNodeIdProducer.invoke(nodeMap)
            val nodeId = childNodeIdProducer.invoke(nodeMap)

            val cloudParentNode = branch.getNode(parentNodeId)
            val cloudChildNode = branch.getNode(nodeId)
            cloudParentNode.removeChild(cloudChildNode)

            nodeMap.remove(nodeId)
        }

    /**
     * Sets outgoing reference of the source node to the target node. The corresponding modelix elements are fetched
     * from the [nodeMap].
     *
     * @param mpsReferenceLink the outgoing MPS reference.
     * @param sourceNode the source [SNode] from which [mpsReferenceLink] is outgoing.
     * @param targetNode the reference target [SNode].
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
    fun setReference(
        mpsReferenceLink: SReferenceLink,
        sourceNode: SNode,
        targetNode: SNode?,
    ) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val sourceNodeId = nodeMap[sourceNode]!!
            val cloudNode = branch.getNode(sourceNodeId)
            val reference = MPSReferenceLink(mpsReferenceLink)

            if (targetNode == null) {
                cloudNode.setReferenceTarget(reference, null as INode?)
            } else if (targetNode.model?.isReadOnly == true) {
                val serialized = MPSNodeReference(targetNode.reference).serialize()
                val nodeReference = NodeReference(serialized)
                cloudNode.setReferenceTarget(reference, nodeReference)
            } else {
                val targetNodeId = nodeMap[targetNode]!!
                val targetModelixNode = branch.getNode(targetNodeId)
                cloudNode.setReferenceTarget(reference, targetModelixNode)
            }
        }
    }

    /**
     * Resolves the unresolved node references between the modelix nodes. An unresolved reference was created, if the
     * target modelix node was not available at the time, when we wanted to set the reference to the target modelix
     * node. This method resolves such references in [resolveReferences] and then clears the collection.
     */
    fun resolveReferences() {
        resolvableReferences?.forEach { setReferenceInTheCloud(it.sourceNode, it.referenceLink, it.mpsTargetNode) }
        resolvableReferences?.clear()
    }
}

/**
 * Represents a reference between two modelix nodes that could not be resolved at the time it was created (because the
 * target modelix node did not exist at that time).
 *
 * @property sourceNode the modelix node from which the reference is outgoing.
 * @property referenceLink the reference between the two nodes.
 * @property mpsTargetNode the MPS [SNode] whose corresponding modelix node has to be the [referenceLink] target.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class CloudResolvableReference(
    val sourceNode: INode,
    val referenceLink: IReferenceLink,
    val mpsTargetNode: SNode?,
)
