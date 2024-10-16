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
import jetbrains.mps.smodel.SModelId
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
import org.modelix.model.api.ILanguageRepository
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

/**
 * Transforms a modelix [INode] that represents an MPS Model to the corresponding [SModel], or to concepts related to
 * that (e.g., Model Imports, Language / DevKit Dependencies, etc.). Besides, it transforms the changes that occurred
 * to its properties or references on the modelix side to the corresponding changes on the MPS side.
 *
 * @param mpsLanguageRepository the [ILanguageRepository] that can resolve Concept UIDs of modelix nodes to Concepts in
 * MPS.
 *
 * @property branch the modelix branch we are connected to.
 * @property serviceLocator a collector class to simplify injecting the commonly used services in the sync plugin.
 */
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
     * The active [SRepository] to access the [SModel]s and [SModule]s in MPS.
     */
    private val mpsRepository = serviceLocator.mpsRepository

    /**
     * The MPS Node to modelix node transformer.
     */
    private val nodeTransformer = NodeTransformer(branch, serviceLocator, mpsLanguageRepository)

    /**
     * Model Imports that shall be resolved later, because the target [SModel] might not exist yet in MPS.
     */
    private val resolvableModelImports = mutableListOf<ResolvableModelImport>()

    /**
     * Transforms a modelix node, identified by its [nodeId], to an [SModel] completely (i.e., all contained model
     * nodes, Model Imports, Language / DevKit Dependencies are transformed. Note: the target models of the Model
     * Imports are not transformed. We assume that the target Model eventually exists in MPS.).
     *
     * The transformed elements are automatically added to the project in MPS and are not returned by the transformation
     * methods.
     *
     * @param nodeId the identifier of the modelix node that represents the [SModel].
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
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

    /**
     * Transforms a modelix node, identified by its [nodeId], to an [SModel]. The contained children model nodes and
     * Language / DevKit Dependencies are not transformed, in contrast to [transformToModelCompletely].
     *
     * The transformed elements are automatically added to the project in MPS and are not returned by the transformation
     * methods.
     *
     * @param nodeId the identifier of the modelix node that represents the [SModel].
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
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

    /**
     * Transforms the modelix node identified by its [nodeId] into a Model Import in MPS and adds this Model Import
     * to the source Model. If the target Model is not in MPS yet, then the Model Import is put into the
     * [resolveModelImports] and will be manually resolved by [resolveModelImports].
     *
     * The transformed elements are automatically added to the project in MPS and are not returned by the transformation
     * methods.
     *
     * @param nodeId the identifier of the modelix node that represents the Model Import.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
    fun transformModelImport(nodeId: Long) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
            val sourceModel = nodeMap.getModel(iNode.getModel()?.nodeIdAsLong())!!

            val targetModelRef =
                iNode.getReferenceTargetRef(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)!!
            val serializedModelRef = targetModelRef.serialize()

            val targetIsAnINode = PNodeReference.tryDeserialize(serializedModelRef) != null
            if (targetIsAnINode) {
                val targetModel =
                    iNode.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)!!
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

    /**
     * Resolves the Model Imports stored in [resolveModelImports]. The target Model of such Model Imports were not
     * available in MPS yet at the time they were created. Resolving Model Import means that we create a Model Import
     * (an [SModelReference]) in the source Model that points to the target Model that is being imported.
     *
     * The transformed elements are automatically added to the project in MPS and are not returned by the transformation
     * methods.
     *
     * @param repository the active [SRepository] to access the [SModel]s in MPS.
     */
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

    /**
     * Resolves the Model Imports, and resolves the unresolved node references.
     *
     * The transformed elements are automatically added to the project in MPS and are not returned by the transformation
     * methods.
     *
     * @param repository the active [SRepository] to access the [SModel]s in MPS.
     *
     * @see [resolveModelImports].
     * @see [NodeTransformer.resolveReferences].
     */
    fun resolveCrossModelReferences(repository: SRepository) {
        resolveModelImports(repository)
        nodeTransformer.resolveReferences()
    }

    /**
     * Handles a property change event in modelix, that should be played into MPS. This event occurs if a property of
     * a modelix node changed, and this property represents an [SModel] in MPS.
     *
     * @param sModel the [SModel] whose property changed.
     * @param role the name or UID of the property.
     * @param newValue the new value of the property.
     * @param nodeId the identifier of the modelix node that represents the [SModel] and whose property changed.
     * @param usesRoleIds shows if [role] is a human-readable name or a UID.
     */
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

    /**
     * Handles a parent changed event in modelix, that should be played into MPS. This event occurs if a modelix node,
     * that represents an [SModel], is moved to a new parent node, that represents an [SModule].
     *
     * @param newParentId the identifier of the modelix node that is the new parent, and represents an [SModule].
     * @param nodeId the identifier of the modelix node that represents the [SModel] that was moved to a new parent.
     * @param sModel the [SModel] who was moved to a new parent.
     */
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

    /**
     * Handles a node removed event in modelix. If a node that represents an [SModel] is deleted in modelix, then it
     * should be also removed in MPS.
     *
     * @param sModel the [SModel] that should be removed in MPS.
     * @param nodeId the identifier of the modelix node that represents the [SModel] that was deleted.
     */
    fun modelDeleted(sModel: SModel, nodeId: Long) {
        ModelDeleteHelper(sModel).delete()
        nodeMap.remove(nodeId)
    }

    /**
     * Handles a node removed event in modelix. If a node that represents a Model Import is deleted in modelix, then it
     * should be also removed in modelix.
     *
     * @param outgoingModelReference represents a Model Import in MPS that should be deleted.
     */
    fun modeImportDeleted(outgoingModelReference: ModelWithModelReference) {
        val model = outgoingModelReference.sourceModelReference.resolve(mpsRepository)
        ModelImports(model).removeModelImport(outgoingModelReference.modelReference)
        nodeMap.remove(outgoingModelReference)
    }

    /**
     * Handles a node removed event in modelix. If a node that represents a Module Dependency (i.e., a Language / DevKit
     * Dependency) of a Model is deleted in modelix, then it should be also removed in modelix.
     *
     * @param modelWithModuleReference represents a Module Dependency of a Model in MPS that should be deleted.
     */
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

    /**
     * @param iNode the modelix node that may represent a Descriptor Model.
     *
     * @return true if [iNode] represents a Descriptor Model (i.e., it's name ends with [descriptorSuffix]).
     */
    private fun isDescriptorModel(iNode: INode): Boolean {
        val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
        return iNode.isModel() && name?.endsWith(descriptorSuffix) == true
    }

    /**
     * Notifies the user about the error [message] and logs this message via the [logger] too.
     *
     * @param message the error to notify the user about.
     */
    private fun notifyAndLogError(message: String) {
        val exception = ModelixToMpsSynchronizationException(message)
        notifier.notifyAndLogError(message, exception, logger)
    }

    /**
     * Notifies the user about the error [message], its [cause] and logs this message via the [logger] too.
     *
     * @param message the error to notify the user about.
     * @param cause the cause of the error.
     */
    private fun notifyAndLogError(message: String, cause: Exception) {
        val exception = ModelixToMpsSynchronizationException(message, cause)
        notifier.notifyAndLogError(message, exception, logger)
    }
}

/**
 * Represents a Model Import that should be resolved at a later point in time (e.g., because the target Model was not
 * available in MPS, when this object was created).
 *
 * @property source the source MPS model that should contain the Model Import.
 * @property targetModelId the [SModelId] of the target [SModel].
 * @property targetModelModelixId the identifier of the modelix node that represents the target [SModel].
 * @property modelReferenceNodeId the identifier of the modelix node that represents the Model Import.
 */
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
