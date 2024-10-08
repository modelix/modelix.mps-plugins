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
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.exceptions.NodeAlreadySynchronizedException

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class NodeSynchronizer(
    private val branch: IBranch,
    private val resolvableReferences: MutableCollection<CloudResolvableReference>? = null,
    serviceLocator: ServiceLocator,
) {

    private val nodeMap = serviceLocator.nodeMap
    private val syncQueue = serviceLocator.syncQueue

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

    private fun throwExceptionIfChildExists(cloudParentNode: INode, childLink: IChildLink, node: SNode) {
        val children = cloudParentNode.getChildren(childLink)
        val nodeExistsOnTheServer = children.any { node.getModelixId() == it.getOriginalReference() }
        val isSynchedToMps = nodeMap.isMappedToModelix(node)
        if (nodeExistsOnTheServer && !isSynchedToMps) {
            throw NodeAlreadySynchronizedException(node)
        }
    }

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

    fun setProperty(property: IProperty, newValue: String, sourceNodeIdProducer: (MpsToModelixMap) -> Long) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE), SyncDirection.MPS_TO_MODELIX) {
            val nodeId = sourceNodeIdProducer.invoke(nodeMap)
            val cloudNode = branch.getNode(nodeId)
            cloudNode.setPropertyValue(property, newValue)
        }

    fun removeNode(parentNodeIdProducer: (MpsToModelixMap) -> Long, childNodeIdProducer: (MpsToModelixMap) -> Long) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE), SyncDirection.MPS_TO_MODELIX) {
            val parentNodeId = parentNodeIdProducer.invoke(nodeMap)
            val nodeId = childNodeIdProducer.invoke(nodeMap)

            val cloudParentNode = branch.getNode(parentNodeId)
            val cloudChildNode = branch.getNode(nodeId)
            cloudParentNode.removeChild(cloudChildNode)

            nodeMap.remove(nodeId)
        }

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

    fun resolveReferences() {
        resolvableReferences?.forEach { setReferenceInTheCloud(it.sourceNode, it.referenceLink, it.mpsTargetNode) }
        resolvableReferences?.clear()
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class CloudResolvableReference(
    val sourceNode: INode,
    val referenceLink: IReferenceLink,
    val mpsTargetNode: SNode?,
)
