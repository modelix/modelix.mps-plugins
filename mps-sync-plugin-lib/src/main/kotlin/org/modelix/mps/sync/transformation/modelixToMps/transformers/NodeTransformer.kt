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

package org.modelix.mps.sync.transformation.modelixToMps.transformers

import jetbrains.mps.project.DevKit
import jetbrains.mps.project.ModuleId
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.IChildLink
import org.modelix.model.api.INode
import org.modelix.model.api.getNode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.model.mpsadapters.MPSProperty
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.factories.SNodeFactory
import org.modelix.mps.sync.mps.notifications.InjectableNotifierWrapper
import org.modelix.mps.sync.mps.util.addDevKit
import org.modelix.mps.sync.mps.util.addLanguageImport
import org.modelix.mps.sync.tasks.ContinuableSyncTask
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.ModelixToMpsSynchronizationException
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.util.getModel
import org.modelix.mps.sync.util.isDevKitDependency
import org.modelix.mps.sync.util.isModel
import org.modelix.mps.sync.util.isModule
import org.modelix.mps.sync.util.isSingleLanguageDependency
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.UUID
import kotlin.reflect.KFunction2

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
class NodeTransformer(private val branch: IBranch, mpsLanguageRepository: MPSLanguageRepository) {

    private val logger = KotlinLogging.logger {}
    private val nodeMap = MpsToModelixMap
    private val syncQueue = SyncQueue
    private val notifierInjector = InjectableNotifierWrapper

    private val nodeFactory = SNodeFactory(mpsLanguageRepository, nodeMap, syncQueue, branch)

    fun transformToNode(iNode: INode): ContinuableSyncTask {
        val nodeId = iNode.nodeIdAsLong()
        return if (iNode.isDevKitDependency()) {
            transformDevKitDependency(nodeId)
        } else if (iNode.isSingleLanguageDependency()) {
            transformLanguageDependency(nodeId)
        } else {
            transformNode(nodeId, nodeFactory::createNodeRecursively)
        }
    }

    fun transformNode(
        nodeId: Long,
        nodeFactoryMethod: KFunction2<Long, SModel?, ContinuableSyncTask> = nodeFactory::createNode,
    ) = syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
        val iNode = branch.getNode(nodeId)
        val modelId = iNode.getModel()?.nodeIdAsLong()
        val model = nodeMap.getModel(modelId)
        val isTransformed = nodeMap.isMappedToMps(nodeId)
        if (isTransformed) {
            logger.info { "Node $nodeId is already transformed." }
        } else {
            if (model == null) {
                val message =
                    "Node $nodeId(${iNode.concept?.getLongName() ?: "concept null"}) was not transformed, because model is null."
                notifyAndLogError(message)
            } else {
                return@enqueue nodeFactoryMethod.invoke(nodeId, model).getResult()
            }
        }
    }

    fun transformLanguageOrDevKitDependency(iNode: INode): ContinuableSyncTask {
        val nodeId = iNode.nodeIdAsLong()
        return if (iNode.isDevKitDependency()) {
            transformDevKitDependency(nodeId)
        } else if (iNode.isSingleLanguageDependency()) {
            transformLanguageDependency(nodeId)
        } else {
            val message =
                "Node ($nodeId) is not transformed, because it is neither DevKit nor SingleLanguageDependency."
            notifyAndLogError(message)
            throw IllegalStateException(message)
        }
    }

    fun transformLanguageDependency(nodeId: Long) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
            val dependentModule = getDependentModule(iNode)
            val languageModuleReference = (dependentModule as Language).moduleReference
            val sLanguage = MetaAdapterFactory.getLanguage(languageModuleReference)
            val languageVersion =
                iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version)!!
                    .toInt()

            val parent = iNode.parent!!
            val parentNodeId = parent.nodeIdAsLong()
            val parentIsModule = parent.isModule()
            val parentIsModel = parent.isModel()

            if (parentIsModule) {
                val parentModule = nodeMap.getModule(parentNodeId)!!
                parentModule.models.forEach {
                    it.addLanguageImport(sLanguage, languageVersion)
                    nodeMap.put(it, languageModuleReference, iNode.nodeIdAsLong())
                }
            } else if (parentIsModel) {
                val parentModel = nodeMap.getModel(parentNodeId)!!
                parentModel.addLanguageImport(sLanguage, languageVersion)
                nodeMap.put(parentModel, languageModuleReference, iNode.nodeIdAsLong())
            } else {
                val message =
                    "Node (${iNode.nodeIdAsLong()})'s parent is neither a Module nor a Model, thus the Language Dependency is not added to the Module/Model."
                notifyAndLogError(message)
            }
        }

    fun transformDevKitDependency(nodeId: Long) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MPS_WRITE, SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
            val dependentModule = getDependentModule(iNode)
            val devKitModuleReference = (dependentModule as DevKit).moduleReference

            val parent = iNode.parent!!
            val parentNodeId = parent.nodeIdAsLong()
            val parentIsModule = parent.isModule()
            val parentIsModel = parent.isModel()

            if (parentIsModule) {
                val parentModule = nodeMap.getModule(parentNodeId)!!
                parentModule.models.forEach {
                    it.addDevKit(devKitModuleReference)
                    nodeMap.put(it, devKitModuleReference, iNode.nodeIdAsLong())
                }
            } else if (parentIsModel) {
                val parentModel = nodeMap.getModel(parentNodeId)!!
                parentModel.addDevKit(devKitModuleReference)
                nodeMap.put(parentModel, devKitModuleReference, iNode.nodeIdAsLong())
            } else {
                val message =
                    "Node (${iNode.nodeIdAsLong()})'s parent is neither a Module nor a Model, thus DevKit is not added to the Module/Model."
                notifyAndLogError(message)
            }
        }

    fun resolveReferences() {
        nodeFactory.resolveReferences()
        nodeFactory.clearResolvableReferences()
    }

    fun nodeDeleted(sNode: SNode, nodeId: Long) {
        sNode.delete()
        nodeMap.remove(nodeId)
    }

    fun nodePropertyChanged(sNode: SNode, role: String, nodeId: Long, newValue: String?, usesRoleIds: Boolean) {
        val sProperty =
            sNode.concept.properties.find {
                if (usesRoleIds) {
                    role == MPSProperty(it).getUID()
                } else {
                    role == it.name
                }
            }
        if (sProperty == null) {
            val message =
                "Property cannot be set, because Node ($nodeId)'s Concept (${sNode.concept.name}) does not have property called $role."
            notifyAndLogError(message)
            return
        }

        val oldValue = sNode.getProperty(sProperty)
        if (oldValue != newValue) {
            sNode.setProperty(sProperty, newValue)
        }
    }

    fun nodeMovedToNewParent(
        newParentId: Long,
        sNode: SNode,
        containmentLink: IChildLink,
        nodeId: Long,
    ) {
        // node moved to a new parent node
        val newParentNode = nodeMap.getNode(newParentId)
        newParentNode?.let {
            nodeMovedToNewParentNode(sNode, newParentNode, containmentLink, nodeId)
            return
        }

        // node moved to a new parent model
        val newParentModel = nodeMap.getModel(newParentId)
        newParentModel?.let {
            nodeMovedToNewParentModel(sNode, newParentModel)
            return
        }

        val message =
            "Node ($nodeId) was neither moved to a new parent node nor to a new parent model, because Node $newParentId was not mapped yet."
        notifyAndLogError(message)
    }

    private fun nodeMovedToNewParentNode(sNode: SNode, newParent: SNode, containmentLink: IChildLink, nodeId: Long) {
        val oldParent = sNode.parent
        if (oldParent == newParent) {
            return
        }

        val containmentLinkName = containmentLink.getSimpleName()
        val containment = newParent.concept.containmentLinks.find { it.name == containmentLinkName }
        if (containment == null) {
            val message =
                "Node cannot be moved to new parent, because Node ($nodeId)'s Concept (${sNode.concept.name}) does not have Containment Link called $containmentLinkName."
            notifyAndLogError(message)
            return
        }

        // remove from old parent
        oldParent?.removeChild(sNode)
        sNode.model?.removeRootNode(sNode)

        // add to new parent
        newParent.addChild(containment, sNode)
    }

    private fun nodeMovedToNewParentModel(sNode: SNode, newParentModel: SModel) {
        val parentModel = sNode.model
        if (parentModel == newParentModel) {
            return
        }

        // remove from old parent
        parentModel?.removeRootNode(sNode)
        sNode.parent?.removeChild(sNode)

        // add to new parent
        newParentModel.addRootNode(sNode)
    }

    private fun getDependentModule(iNode: INode): SModule {
        val uuid = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)!!
        val activeProject = ActiveMpsProjectInjector.activeMpsProject!!
        return activeProject.repository.getModule(ModuleId.regular(UUID.fromString(uuid)))!!
    }

    private fun notifyAndLogError(message: String) {
        val exception = ModelixToMpsSynchronizationException(message)
        notifierInjector.notifyAndLogError(message, exception, logger)
    }
}
