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

import jetbrains.mps.extapi.model.SModelBase
import mu.KotlinLogging
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.NodeReference
import org.modelix.model.api.getNode
import org.modelix.model.mpsadapters.MPSModelImportReference
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.EmptyBinding
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.modelix.util.nodeIdAsLong
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.mps.util.getModelixId
import org.modelix.mps.sync.mps.util.isDescriptorModel
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.transformation.exceptions.ModelAlreadySynchronized
import org.modelix.mps.sync.transformation.exceptions.ModelAlreadySynchronizedException
import org.modelix.mps.sync.transformation.exceptions.MpsToModelixSynchronizationException
import org.modelix.mps.sync.util.synchronizedLinkedHashSet
import org.modelix.mps.sync.util.waitForCompletionOfEachTask

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelSynchronizer(
    private val branch: IBranch,
    private val serviceLocator: ServiceLocator,
    postponeReferenceResolution: Boolean = false,
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
     * The registry to store the [IBinding]s.
     */
    private val bindingsRegistry = serviceLocator.bindingsRegistry

    /**
     * A notifier that can notify the user about certain messages in a nicer way than just simply logging the message.
     */
    private val notifier = serviceLocator.wrappedNotifier

    /**
     * Synchronizes an [org.jetbrains.mps.openapi.model.SNode] to an [org.modelix.model.api.INode] on the model server.
     */
    private val nodeSynchronizer = if (postponeReferenceResolution) {
        NodeSynchronizer(branch, synchronizedLinkedHashSet(), serviceLocator)
    } else {
        NodeSynchronizer(branch, serviceLocator = serviceLocator)
    }

    private val resolvableModelImports = synchronizedLinkedHashSet<CloudResolvableModelImport>()

    fun addModelAndActivate(model: SModelBase) {
        addModel(model)
            .continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
                (it as IBinding).activate()
            }
    }

    fun addModel(model: SModelBase) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            // We do not track changes in descriptor models. See ModelTransformer.isDescriptorModel()
            if (model.isDescriptorModel()) {
                return@enqueue ModelAlreadySynchronized(model)
            }

            val parentModule = model.module
            if (parentModule == null) {
                val message = "Model ($model) cannot be synchronized to the server, because its Module is null."
                notifyAndLogError(message)
                throw IllegalStateException(message)
            }

            val moduleModelixId = nodeMap[parentModule]
            if (moduleModelixId == null) {
                val message =
                    "Model ($model) cannot be synchronized to the server, because its Module ($parentModule) is not found in the local sync cache."
                notifyAndLogError(message)
                throw IllegalStateException(message)
            }
            val cloudModule = branch.getNode(moduleModelixId)
            val childLink = BuiltinLanguages.MPSRepositoryConcepts.Module.models

            // duplicate check
            val modelId = model.getModelixId()
            val modelExists = cloudModule.getChildren(childLink)
                .any { modelId == it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id) }
            if (modelExists) {
                if (nodeMap.isMappedToModelix(model)) {
                    return@enqueue ModelAlreadySynchronized(model)
                } else {
                    throw ModelAlreadySynchronizedException(model)
                }
            }

            val cloudModel = cloudModule.addNewChild(childLink, -1, BuiltinLanguages.MPSRepositoryConcepts.Model)
            nodeMap.put(model, cloudModel.nodeIdAsLong())
            synchronizeModelProperties(cloudModel, model)

            // synchronize root nodes
            model.rootNodes.waitForCompletionOfEachTask(futuresWaitQueue) { nodeSynchronizer.addNode(it) }
        }.continueWith(linkedSetOf(SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) { statusToken ->
            if (statusToken is ModelAlreadySynchronized) {
                return@continueWith statusToken
            }

            // synchronize model imports
            model.modelImports.waitForCompletionOfEachTask(futuresWaitQueue) { addModelImport(model, it) }
        }.continueWith(linkedSetOf(SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) { statusToken ->
            if (statusToken is ModelAlreadySynchronized) {
                return@continueWith statusToken
            }

            // synchronize language dependencies
            model.importedLanguageIds()
                .waitForCompletionOfEachTask(futuresWaitQueue) { addLanguageDependency(model, it) }
        }.continueWith(linkedSetOf(SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) { statusToken ->
            if (statusToken is ModelAlreadySynchronized) {
                return@continueWith statusToken
            }

            // synchronize devKits
            model.importedDevkits().waitForCompletionOfEachTask(futuresWaitQueue) { addDevKitDependency(model, it) }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.MPS_TO_MODELIX) { statusToken ->
            val isDescriptorModel = model.name.value.endsWith("@descriptor")
            if (isDescriptorModel || statusToken is ModelAlreadySynchronized) {
                EmptyBinding()
            } else {
                // register binding
                val binding = ModelBinding(model, branch, serviceLocator)
                bindingsRegistry.addModelBinding(binding)
                binding
            }
        }

    private fun synchronizeModelProperties(cloudModel: INode, model: SModel) {
        cloudModel.setPropertyValue(
            BuiltinLanguages.MPSRepositoryConcepts.Model.id,
            // if you change this property here, please also change above where we check if the model already exists in its parent node
            model.getModelixId(),
        )

        cloudModel.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, model.name.value)

        if (model.name.hasStereotype()) {
            cloudModel.setPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.stereotype, model.name.stereotype)
        }
    }

    fun addModelImport(model: SModel, importedModelReference: SModelReference) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val targetModel = importedModelReference.resolve(model.repository)
            val isNotMapped = nodeMap[targetModel] == null

            if (isNotMapped) {
                resolvableModelImports.add(CloudResolvableModelImport(model, targetModel))
            } else {
                addModelImportToCloud(model, targetModel)
            }
        }

    private fun addModelImportToCloud(source: SModel, targetModel: SModel) =
        if (targetModel.isReadOnly) {
            addReadOnlyModelImportToCloud(source, targetModel)
        } else {
            addNormalModelImportToCloud(source, targetModel)
        }

    private fun addReadOnlyModelImportToCloud(source: SModel, targetModel: SModel) {
        val modelixId = requireNotNull(nodeMap[source]) { "SModel '$source' is not found in the synchronization map." }
        val cloudParentNode = branch.getNode(modelixId)
        val childLink = BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports

        val targetModelReference = BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model
        val serialized = MPSModelImportReference(targetModel.reference, source.reference).serialize()
        val targetModelNodeReference = NodeReference(serialized)

        // duplicate check and sync
        val modelImportExists = cloudParentNode.getChildren(childLink).any {
            val targetRef = it.getReferenceTargetRef(targetModelReference)
            targetRef is NodeReference && targetRef.serialized == targetModelNodeReference.serialized
        }
        if (modelImportExists) {
            val message =
                "Model Import for Model '${targetModel.name}' from Model '${source.name}' will not be synchronized, because it already exists on the server."
            notifier.notifyAndLogWarning(message, logger)
            return
        }

        // warning: might be fragile, because we synchronize the ModelReference's fields by hand
        val cloudModelReference =
            cloudParentNode.addNewChild(childLink, -1, BuiltinLanguages.MPSRepositoryConcepts.ModelReference)

        nodeMap.put(source, targetModel.reference, cloudModelReference.nodeIdAsLong())

        cloudModelReference.setReferenceTarget(targetModelReference, targetModelNodeReference)
    }

    private fun addNormalModelImportToCloud(source: SModel, targetModel: SModel) {
        val modelixId = requireNotNull(nodeMap[source]) { "SModel '$source' is not found in the synchronization map." }
        val cloudParentNode = branch.getNode(modelixId)
        val childLink = BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports

        val targetModelReference = BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model
        val targetModelModelixId = nodeMap[targetModel]!!
        val cloudTargetModel = branch.getNode(targetModelModelixId)
        val idProperty = BuiltinLanguages.MPSRepositoryConcepts.Model.id
        val cloudTargetModelId = cloudTargetModel.getPropertyValue(idProperty)

        // duplicate check and sync
        val modelImportExists = cloudParentNode.getChildren(childLink).any {
            cloudTargetModelId == it.getReferenceTarget(targetModelReference)?.getPropertyValue(idProperty)
        }
        if (modelImportExists) {
            val message =
                "Model Import for Model '${targetModel.name}' from Model '${source.name}' will not be synchronized, because it already exists on the server."
            notifier.notifyAndLogWarning(message, logger)
            return
        }

        // warning: might be fragile, because we synchronize the ModelReference's fields by hand
        val cloudModelReference =
            cloudParentNode.addNewChild(childLink, -1, BuiltinLanguages.MPSRepositoryConcepts.ModelReference)

        nodeMap.put(source, targetModel.reference, cloudModelReference.nodeIdAsLong())

        cloudModelReference.setReferenceTarget(targetModelReference, cloudTargetModel)
    }

    fun addLanguageDependency(model: SModel, language: SLanguage) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val modelixId = nodeMap[model]!!
            val cloudNode = branch.getNode(modelixId)
            val childLink = BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages

            val languageModuleReference = language.sourceModuleReference
            val targetLanguageName = languageModuleReference?.moduleName
            val targetLanguageId = languageModuleReference?.getModelixId()

            // duplicate check and sync
            val dependencyExists = cloudNode.getChildren(childLink).any {
                targetLanguageId == it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)
            }
            if (dependencyExists) {
                val message =
                    "Model '${model.name}''s Language Dependency for '$targetLanguageName' will not be synchronized, because it already exists on the server."
                notifier.notifyAndLogWarning(message, logger)
                return@enqueue null
            }

            val cloudLanguageDependency =
                cloudNode.addNewChild(childLink, -1, BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency)

            nodeMap.put(model, languageModuleReference, cloudLanguageDependency.nodeIdAsLong())

            // warning: might be fragile, because we synchronize the properties by hand
            cloudLanguageDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name,
                targetLanguageName,
            )

            cloudLanguageDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid,
                targetLanguageId,
            )

            cloudLanguageDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency.version,
                model.module.getUsedLanguageVersion(language).toString(),
            )
        }

    fun addDevKitDependency(model: SModel, devKit: SModuleReference) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            val modelixId = nodeMap[model]!!
            val cloudNode = branch.getNode(modelixId)
            val childLink = BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages

            val repository = model.repository
            val devKitModuleId = devKit.moduleId
            val devKitModule = repository.getModule(devKitModuleId)
            val devKitName = devKitModule?.moduleName
            val devKitId = devKitModule?.getModelixId()

            // duplicate check and sync
            val dependencyExists = cloudNode.getChildren(childLink)
                .any { devKitId == it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid) }
            if (dependencyExists) {
                val message =
                    "Model '${model.name}''s DevKit Dependency for '$devKitName' will not be synchronized, because it already exists on the server."
                notifier.notifyAndLogWarning(message, logger)
                return@enqueue null
            }

            val cloudDevKitDependency =
                cloudNode.addNewChild(childLink, -1, BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency)

            nodeMap.put(model, devKit, cloudDevKitDependency.nodeIdAsLong())

            // warning: might be fragile, because we synchronize the properties by hand
            cloudDevKitDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name,
                devKitName,
            )

            cloudDevKitDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid,
                devKitId,
            )
        }

    fun resolveModelImportsInTask() =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            resolveModelImports()
        }

    fun resolveCrossModelReferences() {
        resolveModelImports()
        // resolve (cross-model) references
        nodeSynchronizer.resolveReferences()
    }

    private fun resolveModelImports() {
        resolvableModelImports.forEach {
            try {
                addModelImportToCloud(it.sourceModel, it.targetModel)
                it.isResolved = true
            } catch (ex: Exception) {
                it.isResolved = false
            }
        }
        resolvableModelImports.removeIf { it.isResolved }
    }

    private fun notifyAndLogError(message: String) {
        val exception = MpsToModelixSynchronizationException(message)
        notifier.notifyAndLogError(message, exception, logger)
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class CloudResolvableModelImport(
    val sourceModel: SModel,
    val targetModel: SModel,
    var isResolved: Boolean = false,
)
