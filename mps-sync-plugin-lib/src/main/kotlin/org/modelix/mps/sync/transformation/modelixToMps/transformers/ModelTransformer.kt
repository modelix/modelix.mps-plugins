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

import jetbrains.mps.extapi.model.EditableSModelBase
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.extapi.module.SModuleBase
import jetbrains.mps.model.ModelDeleteHelper
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.structure.modules.ModuleReference
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.ModelImports
import jetbrains.mps.smodel.SModelReference
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
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
import org.modelix.model.mpsadapters.MPSModelImportAsNode
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.EmptyBinding
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.modelix.util.getModel
import org.modelix.mps.sync.modelix.util.getModule
import org.modelix.mps.sync.modelix.util.isModel
import org.modelix.mps.sync.modelix.util.nodeIdAsLong
import org.modelix.mps.sync.mps.ModelRenameHelper
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.mps.util.createModel
import org.modelix.mps.sync.mps.util.deleteDevKit
import org.modelix.mps.sync.mps.util.deleteLanguage
import org.modelix.mps.sync.mps.util.descriptorSuffix
import org.modelix.mps.sync.tasks.ContinuableSyncTask
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.transformation.cache.ModelWithModelReference
import org.modelix.mps.sync.transformation.cache.ModelWithModuleReference
import org.modelix.mps.sync.transformation.exceptions.ModelixToMpsSynchronizationException
import org.modelix.mps.sync.util.waitForCompletionOfEachTask

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelTransformer(
    private val branch: IBranch,
    private val serviceLocator: ServiceLocator,
    mpsLanguageRepository: MPSLanguageRepository,
) {

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
     * The Futures queue of the sync plugin.
     */
    private val futuresWaitQueue = serviceLocator.futuresWaitQueue

    /**
     * A notifier that can notify the user about certain messages in a nicer way than just simply logging the message.
     */
    private val notifier = serviceLocator.wrappedNotifier

    /**
     * The registry to store the [IBinding]s.
     */
    private val bindingsRegistry = serviceLocator.bindingsRegistry

    /**
     * The [jetbrains.mps.project.MPSProject] that is open in the active MPS window.
     */
    private val mpsProject = serviceLocator.mpsProject

    /**
     * The active [SRepository] to access the [org.jetbrains.mps.openapi.model.SModel]s and
     * [org.jetbrains.mps.openapi.module.SModule]s in MPS.
     */
    private val mpsRepository = serviceLocator.mpsRepository

    private val nodeTransformer = NodeTransformer(branch, serviceLocator, mpsLanguageRepository)

    private val resolvableModelImports = mutableListOf<ResolvableModelImport>()

    fun transformToModelCompletely(nodeId: Long) =
        transformToModel(nodeId)
            .continueWith(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
                val model = branch.getNode(nodeId)
                // transform nodes
                model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes)
                    .waitForCompletionOfEachTask(futuresWaitQueue) {
                        nodeTransformer.transformToNode(it)
                    }
            }.continueWith(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
                val model = branch.getNode(nodeId)
                // transform language or DevKit dependencies
                model.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages)
                    .waitForCompletionOfEachTask(futuresWaitQueue) {
                        nodeTransformer.transformLanguageOrDevKitDependency(it)
                    }
            }.continueWith(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_READ), SyncDirection.MODELIX_TO_MPS) {
                val iNode = branch.getNode(nodeId)
                if (isDescriptorModel(iNode)) {
                    EmptyBinding()
                } else {
                    // register binding
                    val model = nodeMap.getModel(iNode.nodeIdAsLong()) as SModelBase
                    val binding = ModelBinding(model, branch, serviceLocator)
                    bindingsRegistry.addModelBinding(binding)
                    binding
                }
            }

    /**
     * Transforms a modelix node, identified by its [nodeId], to an [SModel] and then creates and activates its
     * [ModelBinding].
     *
     * @param nodeId the identifier of the modelix node that represents the [SModel].
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     *
     * @see [transformToModel]
     */
    fun transformToModelAndActivate(nodeId: Long) =
        transformToModel(nodeId)
            .continueWith(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
                val iNode = branch.getNode(nodeId)
                val model = nodeMap.getModel(iNode.nodeIdAsLong()) as SModelBase
                val binding = ModelBinding(model, branch, serviceLocator)
                bindingsRegistry.addModelBinding(binding)
                binding.activate()
            }

    private fun transformToModel(nodeId: Long) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
            val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
            checkNotNull(name) {
                val message = "Node ($iNode) cannot be transformed to Model, because its name is null."
                notifyAndLogError(message)
                message
            }

            val moduleId = iNode.getModule()?.nodeIdAsLong()!!
            val module: SModule? = nodeMap.getModule(moduleId)
            checkNotNull(module) {
                val message =
                    "Node ($iNode) cannot be transformed to Model, because parent Module with ID $moduleId is not found."
                notifyAndLogError(message)
                message
            }

            val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id) ?: ""
            check(serializedId.isNotEmpty()) {
                val message = "Node ($iNode) cannot be transformed to Model, because its ID is null."
                notifyAndLogError(message)
                message
            }
            val modelId = PersistenceFacade.getInstance().createModelId(serializedId)

            if (!isDescriptorModel(iNode)) {
                val sModel = module.createModel(name, modelId) as EditableSModel
                sModel.save()
                nodeMap.put(sModel, iNode.nodeIdAsLong())
            }

            // register model imports
            iNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports)
                .waitForCompletionOfEachTask(futuresWaitQueue) {
                    transformModelImport(it.nodeIdAsLong())
                }
        }

    fun transformModelImport(nodeId: Long) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
            val sourceModel = nodeMap.getModel(iNode.getModel()?.nodeIdAsLong())!!

            val targetModelRef = iNode.getReferenceTargetRef(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)!!
            val serializedModelRef = targetModelRef.serialize()

            val targetIsAnINode = PNodeReference.tryDeserialize(serializedModelRef) != null
            if (targetIsAnINode) {
                val targetModel = iNode.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)!!
                val targetId = targetModel.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id)!!
                // target iNode is probably not transformed yet, therefore delaying the model import resolution
                resolvableModelImports.add(
                    ResolvableModelImport(
                        source = sourceModel,
                        targetModelId = targetId,
                        targetModelModelixId = targetModel.nodeIdAsLong(),
                        modelReferenceNodeId = iNode.nodeIdAsLong(),
                    ),
                )
            } else {
                // target is an SModel in MPS
                val modelixModelImport = MPSArea(mpsRepository).resolveNode(targetModelRef) as MPSModelImportAsNode?
                requireNotNull(modelixModelImport) { "Model Import identified by Node $nodeId is not found." }
                val modelImport = modelixModelImport.importedModel.reference
                ModelImports(sourceModel).addModelImport(modelImport)

                nodeMap.put(sourceModel, modelImport, iNode.nodeIdAsLong())
            }
        }

    fun resolveModelImports(repository: SRepository) {
        resolvableModelImports.forEach {
            val sourceModel = it.source

            val id = PersistenceFacade.getInstance().createModelId(it.targetModelId)
            val targetModel = (nodeMap.getModel(it.targetModelModelixId) ?: repository.getModel(id))
            if (targetModel == null) {
                /*
                 * Issue MODELIX-819:
                 * A manual quick-fix would be if the user downloads the target model (+ its container module) into
                 * their MPS project, then create a module dependency between the source module and the target model's
                 * module, because that is the most probable cause of the problem.
                 * TODO (1) Show it as a suggestion to the user?
                 * TODO (2) If the model is not on the server then there is no 100% sure way to fix the issue, unless
                 * the model has the same ModelId as what is in the ModelImport and that model can be uploaded to the
                 * server. After that see suggestion above TODO (1) to fix the issue.
                 * TODO (3) As a final fallback, the user could remove the model import. In this case, we have to
                 * implement the corresponding feature (e.g., as an action by clicking on a button). Maybe this
                 * direction would be easier for the user and for us too.
                 */
                val message =
                    "ModelImport from Model ${it.source.modelId}(${it.source.name}) to Model $id cannot be resolved, because target model is not found."
                notifyAndLogError(message)
                throw NoSuchElementException(message)
            } else if (targetModel.modelId == sourceModel.modelId) {
                logger.warn { "Ignoring Model Import from Model ${sourceModel.name} (parent Module: ${sourceModel.module.moduleName}) to itself." }
            } else {
                nodeMap.put(targetModel, it.targetModelModelixId)

                val targetModule = targetModel.module
                val moduleReference = ModuleReference(targetModule.moduleName, targetModule.moduleId)
                val modelImport = SModelReference(moduleReference, id, targetModel.name)

                ModelImports(sourceModel).addModelImport(modelImport)
                nodeMap.put(it.source, modelImport, it.modelReferenceNodeId)
            }
        }
        resolvableModelImports.clear()
    }

    fun resolveCrossModelReferences(repository: SRepository) {
        resolveModelImports(repository)
        nodeTransformer.resolveReferences()
    }

    fun modelPropertyChanged(sModel: SModel, role: String, newValue: String?, nodeId: Long, usesRoleIds: Boolean) {
        val modelId = sModel.modelId
        val nameProperty = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name
        val isNameProperty = if (usesRoleIds) {
            role == nameProperty.getUID()
        } else {
            role == nameProperty.getSimpleName()
        }

        val stereotypeProperty = BuiltinLanguages.MPSRepositoryConcepts.Model.stereotype
        val isStereotypeProperty = if (usesRoleIds) {
            role == stereotypeProperty.getUID()
        } else {
            role == stereotypeProperty.getSimpleName()
        }

        if (isNameProperty) {
            val oldValue = sModel.name.value
            if (oldValue != newValue) {
                if (newValue.isNullOrEmpty()) {
                    val message = "Node ($nodeId)'s name cannot be changed, because its null or empty."
                    notifyAndLogError(message)
                    return
                } else if (sModel !is EditableSModelBase) {
                    val message =
                        "SModel ($modelId) is not an EditableSModelBase, therefore it cannot be renamed. Corresponding Node ID is $nodeId."
                    notifyAndLogError(message)
                    return
                }

                ModelRenameHelper(sModel, mpsProject).renameModel(newValue)
            }
        } else if (isStereotypeProperty) {
            val oldValue = sModel.name.stereotype
            if (oldValue != newValue) {
                if (sModel !is EditableSModelBase) {
                    val message =
                        "SModel ($modelId) is not an EditableSModelBase, therefore it cannot be renamed. Corresponding Node ID is $nodeId."
                    notifyAndLogError(message)
                    return
                }

                ModelRenameHelper(sModel, mpsProject).changeStereotype(newValue)
            }
        } else {
            val message =
                "Role $role is unknown for concept Model. Therefore the property is not set. Corresponding Node ID is $nodeId."
            notifyAndLogError(message)
        }
    }

    fun modelMovedToNewParent(newParentId: Long, nodeId: Long, sModel: SModel) {
        val newParentModule = nodeMap.getModule(newParentId)
        if (newParentModule == null) {
            val message =
                "Node ($nodeId) that is a Model, was not moved to a new parent module, because new parent Module (Node ID $newParentId) was not mapped yet."
            notifyAndLogError(message)
            return
        }

        val oldParentModule = sModel.module
        if (oldParentModule == newParentModule) {
            return
        }

        // remove from old parent
        if (oldParentModule !is SModuleBase) {
            val message =
                "Old parent Module ${oldParentModule?.moduleId} of Model ${sModel.modelId} is not an SModuleBase. Therefore parent of Model (Node ID $nodeId) is not changed."
            notifyAndLogError(message)
            return
        } else if (sModel !is SModelBase) {
            val message =
                "Model ${sModel.modelId} is not an SModelBase. Therefore parent of Model (Node ID $nodeId) is not changed."
            notifyAndLogError(message)
            return
        }
        oldParentModule.unregisterModel(sModel)
        sModel.module = null

        // add to new parent
        if (newParentModule !is SModuleBase) {
            val message =
                "New parent Module ${newParentModule.moduleId} is not an SModuleBase. Therefore parent of Model (Node ID $nodeId) is not changed."
            notifyAndLogError(message)
            return
        }
        newParentModule.registerModel(sModel)
    }

    fun modelDeleted(sModel: SModel, nodeId: Long) {
        ModelDeleteHelper(sModel).delete()
        nodeMap.remove(nodeId)
    }

    fun modeImportDeleted(outgoingModelReference: ModelWithModelReference) {
        val model = outgoingModelReference.sourceModelReference.resolve(mpsRepository)
        ModelImports(model).removeModelImport(outgoingModelReference.modelReference)
        nodeMap.remove(outgoingModelReference)
    }

    fun moduleDependencyOfModelDeleted(modelWithModuleReference: ModelWithModuleReference, nodeId: Long) {
        val sourceModel = modelWithModuleReference.sourceModelReference.resolve(mpsRepository)
        val targetModuleReference = modelWithModuleReference.moduleReference
        when (val targetModule = targetModuleReference.resolve(sourceModel.repository)) {
            is Language -> {
                try {
                    val sLanguage = MetaAdapterFactory.getLanguage(targetModuleReference)
                    sourceModel.deleteLanguage(sLanguage)
                    nodeMap.remove(modelWithModuleReference)
                } catch (ex: Exception) {
                    val message =
                        "Language Import ($targetModule) cannot be deleted, because ${ex.message} Corresponding Node ID is $nodeId."
                    notifyAndLogError(message, ex)
                }
            }

            is DevKit -> {
                try {
                    sourceModel.deleteDevKit(targetModuleReference)
                    nodeMap.remove(modelWithModuleReference)
                } catch (ex: Exception) {
                    val message =
                        "DevKit dependency ($targetModule) cannot be deleted, because ${ex.message} Corresponding Node ID is $nodeId."
                    notifyAndLogError(message, ex)
                }
            }

            else -> {
                val message =
                    "Target Module referred by $targetModuleReference is neither a Language nor DevKit. Therefore its dependency it cannot be deleted. Corresponding Node ID is $nodeId."
                notifyAndLogError(message)
            }
        }
    }

    private fun isDescriptorModel(iNode: INode): Boolean {
        val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        return iNode.isModel() && name?.endsWith(descriptorSuffix) == true
    }

    private fun notifyAndLogError(message: String) {
        val exception = ModelixToMpsSynchronizationException(message)
        notifier.notifyAndLogError(message, exception, logger)
    }

    private fun notifyAndLogError(message: String, cause: Exception) {
        val exception = ModelixToMpsSynchronizationException(message, cause)
        notifier.notifyAndLogError(message, exception, logger)
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class ResolvableModelImport(
    val source: SModel,
    val targetModelId: String,
    val targetModelModelixId: Long,
    val modelReferenceNodeId: Long,
)
