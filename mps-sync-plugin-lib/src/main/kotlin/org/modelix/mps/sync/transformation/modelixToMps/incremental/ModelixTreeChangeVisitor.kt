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

package org.modelix.mps.sync.transformation.modelixToMps.incremental

import jetbrains.mps.project.AbstractModule
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.PNodeReference
import org.modelix.model.api.PropertyFromName
import org.modelix.model.api.getNode
import org.modelix.model.mpsadapters.MPSArea
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.model.mpsadapters.MPSNode
import org.modelix.mps.sync.modelix.util.getModule
import org.modelix.mps.sync.modelix.util.isDevKitDependency
import org.modelix.mps.sync.modelix.util.isModel
import org.modelix.mps.sync.modelix.util.isModelImport
import org.modelix.mps.sync.modelix.util.isModule
import org.modelix.mps.sync.modelix.util.isModuleDependency
import org.modelix.mps.sync.modelix.util.isSingleLanguageDependency
import org.modelix.mps.sync.modelix.util.nodeIdAsLong
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.exceptions.ModelixToMpsSynchronizationException
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModelTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.transformation.modelixToMps.transformers.NodeTransformer

/**
 * The change listener that is called, when a change on the modelix branch (on the model server or local) occurred. This
 * change will be played into MPS in a way that the modelix elements are transformed to MPS elements by the
 * corresponding transformer methods.
 *
 * @param serviceLocator a collector class to simplify injecting the commonly used services in the sync plugin.
 * @param languageRepository the [ILanguageRepository] that can resolve Concept UIDs of modelix nodes to Concepts in
 * MPS.
 *
 * @property branch the modelix branch we are connected to.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelixTreeChangeVisitor(
    private val branch: IBranch,
    serviceLocator: ServiceLocator,
    languageRepository: MPSLanguageRepository,
) : ITreeChangeVisitorEx {

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * The lookup map (internal cache) between the MPS elements and the corresponding modelix Nodes.
     */
    private val nodeMap = serviceLocator.nodeMap

    /**
     * The task queue of the sync plugin.
     */
    private val syncQueue = serviceLocator.syncQueue

    /**
     * A notifier that can notify the user about certain messages in a nicer way than just simply logging the message.
     */
    private val notifier = serviceLocator.wrappedNotifier

    /**
     * The active [SRepository] to access the [SModel]s and [SModule]s in MPS.
     */
    private val mpsRepository = serviceLocator.mpsRepository

    /**
     * The MPS Node to modelix node transformer.
     */
    private val nodeTransformer = NodeTransformer(branch, serviceLocator, languageRepository)

    /**
     * The MPS Model to modelix node transformer.
     */
    private val modelTransformer = ModelTransformer(branch, serviceLocator, languageRepository)

    /**
     * The MPS Module to modelix node transformer.
     */
    private val moduleTransformer = ModuleTransformer(branch, serviceLocator, languageRepository)

    /**
     * Transforms a reference changed event to the corresponding changes in MPS. This event handler is called, if a
     * modelix node's outgoing ReferenceLink is changed (i.e., it is set to a target reference or to null).
     *
     * A change in the modelix node's outgoing reference will be transformed to a change in an MPS node's outgoing
     * reference (i.e., the reference target is set to another MPS node or to null).
     *
     * @param nodeId the identifier of the modelix node.
     * @param role the name or ID of the ReferenceLink inside the modelix node.
     */
    override fun referenceChanged(nodeId: Long, role: String) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
            val sNode = nodeMap.getNode(nodeId)
            if (sNode == null) {
                val message =
                    "Node ($nodeId) is not mapped yet, therefore its Reference Link called $role cannot be changed."
                notifyAndLogError(message)
                return@enqueue null
            }

            val sReferenceLink = sNode.concept.referenceLinks.find { it.name == role }
            if (sReferenceLink == null) {
                val message =
                    "Node ($nodeId)'s Concept (${sNode.concept.name}) does not have Reference Link called $role."
                notifyAndLogError(message)
                return@enqueue null
            }

            val iNode = getNode(nodeId)
            val usesRoleIds = iNode.usesRoleIds()
            val iReferenceLink = iNode.getReferenceLinks().find {
                if (usesRoleIds) {
                    role == it.getUID()
                } else {
                    role == it.getSimpleName()
                }
            }
            val targetSNode = iReferenceLink?.let {
                val targetNodeReference = iNode.getReferenceTargetRef(it)
                if (targetNodeReference == null) {
                    null
                } else {
                    val serializedRef = targetNodeReference.serialize()
                    val targetIsAnINode = PNodeReference.tryDeserialize(serializedRef) != null
                    if (targetIsAnINode) {
                        val targetNode = iNode.getReferenceTarget(it)
                            ?: throw IllegalArgumentException("PNodeReference exists, but PNode cannot be resolved: '$serializedRef'")
                        nodeMap.getNode(targetNode.nodeIdAsLong())
                    } else {
                        val area = MPSArea(mpsRepository)
                        val mpsNode = area.resolveNode(targetNodeReference) as MPSNode?
                        requireNotNull(mpsNode) { "SNode identified by Node $nodeId is not found." }
                        mpsNode.node
                    }
                }
            }

            val oldValue = sNode.getReferenceTarget(sReferenceLink)
            if (oldValue != targetSNode) {
                sNode.setReferenceTarget(sReferenceLink, targetSNode)
            }

            null
        }
    }

    /**
     * Transforms a property changed event to the corresponding changes in MPS. This event handler is called, if a
     * modelix node's property is changed (i.e., it is set to a value or to null).
     *
     * A change in the modelix node's property will be transformed to a change in an MPS Node's / Model's / Module's
     * property, depending on which MPS element the modelix node represents.
     *
     * @param nodeId the identifier of the modelix node.
     * @param role the name or ID of the property inside the modelix node.
     */
    override fun propertyChanged(nodeId: Long, role: String) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
            val isMapped = nodeMap.isMappedToMps(nodeId)
            if (!isMapped) {
                val message =
                    "Element represented by Node ($nodeId) is not mapped yet, therefore its $role property cannot be changed."
                notifyAndLogError(message)
                return@enqueue null
            }

            val iNode = getNode(nodeId)
            val usesRoleIds = iNode.usesRoleIds()
            val iProperty = PropertyFromName(role)
            val newValue = iNode.getPropertyValue(iProperty)

            val sNode = nodeMap.getNode(nodeId)
            sNode?.let {
                nodeTransformer.nodePropertyChanged(sNode, role, nodeId, newValue, usesRoleIds)
                return@enqueue null
            }

            val sModel = nodeMap.getModel(nodeId)
            sModel?.let {
                modelTransformer.modelPropertyChanged(sModel, role, newValue, nodeId, usesRoleIds)
                return@enqueue null
            }

            val sModule = nodeMap.getModule(nodeId)
            sModule?.let {
                moduleTransformer.modulePropertyChanged(role, nodeId, sModule, newValue, usesRoleIds)
                return@enqueue null
            }

            val message = "Property setting case for Node ($nodeId) and property ($role) was missed."
            notifyAndLogError(message)

            null
        }
    }

    /**
     * Transforms a modelix node deletion event to an MPS element deletion event. Depending on which MPS element the
     * modelix node represents (based on the [MpsToModelixMap] mapping table), the deletion of that element in MPS will
     * happen.
     *
     * @param nodeId the identifier of the modelix node.
     */
    override fun nodeRemoved(nodeId: Long) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val isMapped = nodeMap.isMappedToMps(nodeId)
            if (!isMapped) {
                logger.info { "Element represented by Node ($nodeId) is already removed." }
                return@enqueue null
            }

            val sNode = nodeMap.getNode(nodeId)
            sNode?.let {
                nodeTransformer.nodeDeleted(it, nodeId)
                return@enqueue null
            }

            val sModel = nodeMap.getModel(nodeId)
            sModel?.let {
                modelTransformer.modelDeleted(sModel, nodeId)
                return@enqueue null
            }

            val sModule = nodeMap.getModule(nodeId)
            sModule?.let {
                moduleTransformer.moduleDeleted(sModule, nodeId)
                return@enqueue null
            }

            val outgoingModelReference = nodeMap.getOutgoingModelReference(nodeId)
            outgoingModelReference?.let {
                modelTransformer.modeImportDeleted(it)
                return@enqueue null
            }

            val outgoingModuleReferenceFromModel = nodeMap.getOutgoingModuleReferenceFromModel(nodeId)
            outgoingModuleReferenceFromModel?.let {
                modelTransformer.moduleDependencyOfModelDeleted(it, nodeId)
                return@enqueue null
            }

            val outgoingModuleReferenceFromModule = nodeMap.getOutgoingModuleReferenceFromModule(nodeId)
            outgoingModuleReferenceFromModule?.let {
                moduleTransformer.outgoingModuleReferenceFromModuleDeleted(outgoingModuleReferenceFromModule, nodeId)
                return@enqueue null
            }

            val message =
                "A removal case for Node ($nodeId) was missed. It can be ignored, if the Node's parent is deleted."
            notifier.notifyAndLogWarning(message, logger)

            null
        }
    }

    /**
     * Transforms a modelix node creation event to the creation of new MPS elements. Depending on the modelix node's
     * concept, different elements will be created in MPS.
     *
     * @param nodeId the identifier of the modelix node.
     */
    override fun nodeAdded(nodeId: Long) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
            val isMapped = nodeMap.isMappedToMps(nodeId)
            if (isMapped) {
                logger.info { "Node ($nodeId) is already mapped, therefore it cannot be added again." }
                return@enqueue null
            }

            val iNode = getNode(nodeId)
            if (iNode.isModule()) {
                moduleTransformer.transformToModuleAndActivate(nodeId)
            } else if (iNode.isModuleDependency()) {
                val moduleNodeId = iNode.getModule()?.nodeIdAsLong()
                val parentModule = nodeMap.getModule(moduleNodeId)!!
                require(parentModule is AbstractModule) {
                    val message =
                        "Parent Module ($moduleNodeId) of Node (${iNode.nodeIdAsLong()}) is not an AbstractModule. Therefore Node cannot be added."
                    notifyAndLogError(message)
                    message
                }
                moduleTransformer.transformModuleDependency(nodeId, parentModule)
            } else if (iNode.isModel()) {
                modelTransformer.transformToModelAndActivate(nodeId)
            } else if (iNode.isModelImport()) {
                modelTransformer.transformModelImport(nodeId)
            } else if (iNode.isSingleLanguageDependency()) {
                nodeTransformer.transformLanguageDependency(nodeId)
            } else if (iNode.isDevKitDependency()) {
                nodeTransformer.transformDevKitDependency(nodeId)
            } else {
                nodeTransformer.transformNode(nodeId)
            }
        }
    }

    /**
     * Transforms a child changed event in modelix. This event occurs if the children of a modelix node, identified by
     * its [nodeId] identifier, have changed.
     *
     * TODO Rethink if we have to limit childrenChanged operation further. It is expected to be called after the
     * [nodeAdded] method and thereby we have to resolve the modelImports and references (see body of this method).
     * However, this method can be also called before/after the [nodeRemoved] operation. Where it does not make sense
     * to resolve the references...
     * Moreover, there is no guarantee in which order the method of this class will be called, due to the undefined
     * order of changes after the Diff calculation.
     *
     * @param nodeId the identifier of the modelix node.
     * @param role the name or ID of the ChildLink inside the modelix node.
     */
    override fun childrenChanged(nodeId: Long, role: String?) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            modelTransformer.resolveModelImports(mpsRepository)
            nodeTransformer.resolveReferences()
            null
        }
    }

    /**
     * Transforms a containment changed event in modelix to the corresponding changes in MPS. The containment change
     * event occurs, if the modelix node, identified by [nodeId], is moved to a new parent node.
     *
     * In MPS, it means moving the corresponding MPS element to a new parent MPS Node / MPS Model or MPS Module,
     * depending on which element the modelix node was mapped to.
     *
     * @param nodeId the identifier of the modelix node.
     */
    override fun containmentChanged(nodeId: Long) {
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
            val nodeIsMapped = nodeMap.isMappedToMps(nodeId)
            if (!nodeIsMapped) {
                val message = "Node ($nodeId) is not mapped yet, therefore it cannot be moved to a new parent."
                notifyAndLogError(message)
                return@enqueue null
            }

            val iNode = getNode(nodeId)
            val newParent = iNode.parent
            if (newParent == null) {
                val message = "Node ($nodeId)'s new parent is null."
                notifyAndLogError(message)
                return@enqueue null
            }
            val newParentId = newParent.nodeIdAsLong()
            val parentIsMapped = nodeMap.isMappedToMps(newParentId)
            if (!parentIsMapped) {
                val message =
                    "Node ($nodeId)'s new parent ($newParentId) is not mapped yet. Therefore Node cannot be moved to a new parent."
                notifyAndLogError(message)
                return@enqueue null
            }

            val containmentLink = iNode.getContainmentLink()
            if (containmentLink == null) {
                val message = "Node ($nodeId)'s containment link is null."
                notifyAndLogError(message)
                return@enqueue null
            }

            val sNode = nodeMap.getNode(nodeId)
            sNode?.let {
                nodeTransformer.nodeMovedToNewParent(newParentId, sNode, containmentLink, nodeId)
                return@enqueue null
            }

            val sModel = nodeMap.getModel(nodeId)
            sModel?.let {
                modelTransformer.modelMovedToNewParent(newParentId, nodeId, sModel)
                return@enqueue null
            }

            val message = "A containment changed case for Node ($nodeId) was missed."
            notifyAndLogError(message)

            null
        }
    }

    /**
     * @param nodeId the identifier of the modelix node.
     *
     * @return the modelix node (identified by [nodeId]) from the [branch].
     */
    private fun getNode(nodeId: Long) = branch.getNode(nodeId)

    /**
     * Notifies the user about the error [message] and logs this message via the [logger] too.
     *
     * @param message the error to notify the user about.
     */
    private fun notifyAndLogError(message: String) {
        val exception = ModelixToMpsSynchronizationException(message)
        notifier.notifyAndLogError(message, exception, logger)
    }
}
