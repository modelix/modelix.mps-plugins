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

package org.modelix.mps.sync.mps.factories

import jetbrains.mps.smodel.adapter.MetaAdapterByDeclaration
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SInterfaceConcept
import org.jetbrains.mps.openapi.language.SReferenceLink
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.getNode
import org.modelix.model.mpsadapters.MPSArea
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.model.mpsadapters.MPSNode
import org.modelix.model.mpsadapters.MPSProperty
import org.modelix.model.mpsadapters.MPSReferenceLink
import org.modelix.mps.sync.modelix.util.getMpsNodeId
import org.modelix.mps.sync.modelix.util.nodeIdAsLong
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.tasks.ContinuableSyncTask
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncTask
import org.modelix.mps.sync.util.waitForCompletionOfEachTask

/**
 * Factory class to create an [SNode] in MPS.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class SNodeFactory(
    private val conceptRepository: MPSLanguageRepository,
    private val branch: IBranch,
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
     * The Futures queue of the sync plugin.
     */
    private val futuresWaitQueue = serviceLocator.futuresWaitQueue

    /**
     * The active [SRepository] to access the [SModel]s and [SModule]s in MPS.
     */
    private val mpsRepository = serviceLocator.mpsRepository

    /**
     * Some references between MPS nodes cannot be resolved at the moment they are created (e.g. because the target node
     * does not exist yet). In this collection we store such references.
     */
    private val resolvableReferences = mutableListOf<ResolvableReference>()

    /**
     * Transform the parameter modelix node denoted by its ID to an [SNode] in MPS and do it recursively for all of its
     * children.
     *
     * @param nodeId the modelix node ID of the node to transform.
     * @param model the [SModel] that is the parent of the node.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
    fun createNodeRecursively(nodeId: Long, model: SModel?): ContinuableSyncTask =
        createNode(nodeId, model)
            .continueWith(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
                val iNode = branch.getNode(nodeId)
                iNode.allChildren.waitForCompletionOfEachTask(futuresWaitQueue) {
                    createNodeRecursively(it.nodeIdAsLong(), model)
                }
            }

    /**
     * Transform the parameter modelix node denoted by its ID to an [SNode] in MPS.
     *
     * @param nodeId the modelix node ID of the node to transform.
     * @param model the [SModel] that is the parent of the node.
     *
     * @return a [ContinuableSyncTask] so that we can chain [SyncTask]s after each other.
     */
    fun createNode(nodeId: Long, model: SModel?): ContinuableSyncTask =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
            val conceptId = iNode.concept?.getUID()!!
            val concept: SConcept = when (val rawConcept = conceptRepository.resolveMPSConcept(conceptId)) {
                is SInterfaceConcept -> {
                    MetaAdapterByDeclaration.asInstanceConcept((rawConcept as SAbstractConcept))
                }

                is SConcept -> {
                    rawConcept
                }

                else -> throw IllegalStateException("Unknown raw concept: $rawConcept")
            }

            // 1. create node
            val mpsNodeId = iNode.getMpsNodeId()
            val sNode = jetbrains.mps.smodel.SNode(concept, mpsNodeId)

            // 2. add to parent
            val parent = iNode.parent
            val parentSerializedModelId =
                parent?.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id) ?: ""
            val parentModelId = if (parentSerializedModelId.isNotEmpty()) {
                PersistenceFacade.getInstance().createModelId(parentSerializedModelId)
            } else {
                null
            }
            val modelIsTheParent = parentModelId != null && model?.modelId == parentModelId
            val isRootNode = concept.isRootable && modelIsTheParent
            if (isRootNode) {
                model?.addRootNode(sNode)
            } else {
                val parentNodeId = parent?.nodeIdAsLong()
                val parentNode = nodeMap.getNode(parentNodeId)
                checkNotNull(parentNode) { "Parent of Node($nodeId) is not found. Node will not be added to the model." }

                val role = iNode.getContainmentLink()
                val containmentLink = parentNode.concept.containmentLinks.first { it.name == role?.getSimpleName() }
                parentNode.addChild(containmentLink, sNode)
            }
            nodeMap.put(sNode, nodeId)

            // 3. set properties
            setProperties(iNode, sNode)

            // 4. set references
            prepareLinkReferences(iNode)
        }

    /**
     * Set all properties of [target] to the same values as they are in the [source].
     *
     * @param source the modelix node from which we read the properties.
     * @param target the MPS node in which we set the properties.
     */
    private fun setProperties(source: INode, target: SNode) {
        target.concept.properties.forEach { sProperty ->
            val property = MPSProperty(sProperty)
            val value = source.getPropertyValue(property)
            target.setProperty(sProperty, value)
        }
    }

    /**
     * Set all outgoing references of the MPS node that was created from the parameter modelix [iNode]. We go through
     * each outgoing reference of the modelix [iNode], search for the corresponding target node in MPS and establish the
     * reference between the two nodes. If the outgoing reference cannot be resolved directly, then it is put in the
     * [resolvableReferences] collection, and will be resolved later (see [resolveReferences]).
     *
     * @param iNode the modelix node that contains the outgoing references
     */
    private fun prepareLinkReferences(iNode: INode) {
        val sourceNodeId = iNode.nodeIdAsLong()
        val source = nodeMap.getNode(sourceNodeId)!!

        iNode.getAllReferenceTargetRefs().forEach {
            val reference = when (val referenceLink = it.first) {
                is MPSReferenceLink -> referenceLink.link
                else -> source.concept.referenceLinks.first { refLink -> refLink.name == referenceLink.getSimpleName() }
            }

            val targetNodeReference = it.second
            val serializedRef = targetNodeReference.serialize()
            val targetIsAnINode = PNodeReference.tryDeserialize(serializedRef) != null

            if (targetIsAnINode) {
                // delay the reference resolution, because the target node might not have been transformed yet
                val targetNode = iNode.getReferenceTarget(it.first)
                    ?: throw IllegalArgumentException("PNodeReference exists, but PNode cannot be resolved: '$serializedRef'")
                val targetNodeId = targetNode.nodeIdAsLong()
                resolvableReferences.add(ResolvableReference(source, reference, targetNodeId))
            } else {
                // target node is an existing SNode
                val area = MPSArea(mpsRepository)
                val mpsNode = area.resolveNode(targetNodeReference) as MPSNode?
                requireNotNull(mpsNode) { "SNode identified by Node $sourceNodeId is not found." }
                source.setReferenceTarget(reference, mpsNode.node)
            }
        }
    }

    /**
     * Resolves the references stored in [resolvableReferences]. I.e. creates the reference links between the source
     * and target [SNode]s in MPS.
     */
    fun resolveReferences() {
        resolvableReferences.forEach {
            val source = it.source
            val reference = it.reference
            val target = nodeMap.getNode(it.targetNodeId)
            source.setReferenceTarget(reference, target)
        }
    }

    /**
     * Clears the [resolveReferences] collection.
     */
    fun clearResolvableReferences() = resolvableReferences.clear()
}

/**
 * Represents a reference between two MPS nodes that could not be resolved at the time the reference was supposed to be
 * created.
 *
 * @property source the source MPS node.
 * @property reference the outgoing reference link of the source MPS node.
 * @property targetNodeId modelix node ID of the target MPS node. The modelix node ID will be resolved to an MPS node in
 * [SNodeFactory.resolveReferences].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class ResolvableReference(
    val source: SNode,
    val reference: SReferenceLink,
    val targetNodeId: Long,
)
