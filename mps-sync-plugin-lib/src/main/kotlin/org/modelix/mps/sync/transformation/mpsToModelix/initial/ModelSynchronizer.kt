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
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
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
import org.modelix.mps.sync.tasks.ContinuableSyncTask
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncTask
import org.modelix.mps.sync.transformation.exceptions.ModelAlreadySynchronized
import org.modelix.mps.sync.transformation.exceptions.ModelAlreadySynchronizedException
import org.modelix.mps.sync.transformation.exceptions.MpsToModelixSynchronizationException
import org.modelix.mps.sync.util.synchronizedLinkedHashSet
import org.modelix.mps.sync.util.waitForCompletionOfEachTask

/**
 * Synchronizes an [SModel] to the modelix model server. This is the class that performs the node operations on the
 * [IBranch], that will be automatically synced to the model server by modelix.
 *
 * @param postponeReferenceResolution if true, then the references between the modelix node will not be resolved when
 * they are to be created.
 *
 * @property branch the modelix branch we are connected to.
 * @property serviceLocator a collector class to simplify injecting the commonly used services in the sync plugin.
 */
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
     * Synchronizes an [SNode] to an [INode] on the model server.
     */
    private val nodeSynchronizer = if (postponeReferenceResolution) {
        NodeSynchronizer(branch, synchronizedLinkedHashSet(), serviceLocator)
    } else {
        NodeSynchronizer(branch, serviceLocator = serviceLocator)
    }

    /**
     * Model Imports between [SModel]s that should be resolved at a later point in time, because the modelix nodes
     * that represent the target [SModel]s might not be available on the [branch] yet.
     */
    private val resolvableModelImports = synchronizedLinkedHashSet<CloudResolvableModelImport>()

    /**
     * Adds the [model] to the model server by synchronizing all its properties, Model Imports, Language and DevKit
     * Dependencies and children nodes recursively. After the synchronization is done, the [ModelBinding] created from
     * the [model] is activated (see [ModelBinding.activate]).
     *
     * Note: that the target models of the Model Imports are not synced implicitly. They are synced as a child model,
     * when their parent [SModule] is added to modelix (see [ModuleSynchronizer.addModule]).
     *
     * Note: the target Languages of Language Dependencies are not synced to the model server, because we assume that
     * the Languages are available in modelix at runtime.
     *
     * @param model the [SModel] to be added to the model server.
     */
    fun addModelAndActivate(model: SModelBase) {
        addModel(model)
            .continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
                (it as IBinding).activate()
            }
    }

    /**
     * Adds the [model] to the model server by synchronizing all its properties, Model Imports, Language and DevKit
     * Dependencies and children nodes recursively. After the synchronization is done, the [ModelBinding] created from
     * the [model] is activated (see [ModelBinding.activate]).
     *
     * Note: that the target models of the Model Imports are not synced implicitly. They are synced as a child model,
     * when their parent [SModule] is added to modelix (see [ModuleSynchronizer.addModule]).
     *
     * Note: the target Languages of Language Dependencies are not synced to the model server, because we assume that
     * the Languages are available in modelix at runtime.
     *
     * @param model the [SModel] to be added to the model server.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed. The result of
     * this task is a [ModelBinding].
     */
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

    /**
     * Synchronizes the properties of the MPS [model] to the modelix node [cloudModel].
     *
     * @param cloudModel the modelix node that represents [model].
     * @param model the MPS Model.
     */
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

    /**
     * Adds the [importedModelReference] as an outgoing Model Import of [model] to the model server. If the target
     * [SModel] has not been synced to modelix yet, then the Model Import will be added to [resolvableModelImports] and
     * will be manually resolved later (see [resolveModelImports]).
     *
     * @param model the MPS Model.
     * @param importedModelReference the outgoing Model Import that will be added to modelix.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
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

    /**
     * Adds a Model Import from [source] to [targetModel] on the model server.
     *
     * @param source the Model Import's source MPS Model.
     * @param targetModel the Model Import's target MPS Model.
     *
     * @see [addReadOnlyModelImportToCloud].
     * @see [addNormalModelImportToCloud].
     */
    private fun addModelImportToCloud(source: SModel, targetModel: SModel) =
        if (targetModel.isReadOnly) {
            addReadOnlyModelImportToCloud(source, targetModel)
        } else {
            addNormalModelImportToCloud(source, targetModel)
        }

    /**
     * Adds a read-only Model Import from [source] to [targetModel] on the model server.
     *
     * @param source the Model Import's source MPS Model.
     * @param targetModel the Model Import's target MPS Model.
     */
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

        // ⚠️ WARNING ⚠️: might be fragile, because we synchronize the ModelReference's fields by hand
        val cloudModelReference =
            cloudParentNode.addNewChild(childLink, -1, BuiltinLanguages.MPSRepositoryConcepts.ModelReference)

        nodeMap.put(source, targetModel.reference, cloudModelReference.nodeIdAsLong())

        cloudModelReference.setReferenceTarget(targetModelReference, targetModelNodeReference)
    }

    /**
     * Adds a normal (not read-only) Model Import from [source] to [targetModel] on the model server.
     *
     * @param source the Model Import's source MPS Model.
     * @param targetModel the Model Import's target MPS Model.
     */
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

        // ⚠️ WARNING ⚠️: might be fragile, because we synchronize the ModelReference's fields by hand
        val cloudModelReference =
            cloudParentNode.addNewChild(childLink, -1, BuiltinLanguages.MPSRepositoryConcepts.ModelReference)

        nodeMap.put(source, targetModel.reference, cloudModelReference.nodeIdAsLong())

        cloudModelReference.setReferenceTarget(targetModelReference, cloudTargetModel)
    }

    /**
     * Adds a Language Dependency from the source [model] to the [language] on the model server.
     *
     * @param model the MPS Model that depends on the target [language].
     * @param language the Language, the [model] uses.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
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

            // ⚠️ WARNING ⚠️: might be fragile, because we synchronize the properties by hand
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

    /**
     * Adds a DevKit Dependency from the source [model] to the [devKit] on the model server.
     *
     * @param model the MPS Model that depends on the target [devKit].
     * @param devKit the DevKit, the [model] uses.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
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

            // ⚠️ WARNING ⚠️: might be fragile, because we synchronize the properties by hand
            cloudDevKitDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name,
                devKitName,
            )

            cloudDevKitDependency.setPropertyValue(
                BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid,
                devKitId,
            )
        }

    /**
     * Runs the [resolveModelImports] in a [SyncTask] asynchronously.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     *
     * @see [resolveModelImports].
     */
    fun resolveModelImportsInTask() =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            resolveModelImports()
        }

    /**
     * Resolves the unresolved Model Imports and the (cross-model) node references. An unresolved Model Import was
     * created if the modelix node that corresponds to the Model Import's target [SModel] was not available on the
     * [branch] when the Model Import was created. An unresolved (cross-model) node reference was created if the modelix
     * node that corresponds to the MPS Node was not available in the [branch] when the node reference was created.
     *
     * @see [resolveModelImports].
     * @see [NodeSynchronizer.resolveReferences].
     */
    fun resolveCrossModelReferences() {
        resolveModelImports()
        // resolve (cross-model) references
        nodeSynchronizer.resolveReferences()
    }

    /**
     * Resolves the unresolved Model Imports that are stored in [resolveModelImports]. An unresolved Model Import was
     * created if the modelix node that corresponds to the Model Import's target [SModel] was not available on the
     * [branch] when the Model Import was created.
     */
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

    /**
     * Notifies the user about the error [message] and logs this message via the [logger] too.
     *
     * @param message the error to notify the user about.
     */
    private fun notifyAndLogError(message: String) {
        val exception = MpsToModelixSynchronizationException(message)
        notifier.notifyAndLogError(message, exception, logger)
    }
}

/**
 * Represents a reference (Model Import) between two modelix nodes (that represent [SModel]s on the model server) that
 * should be created on the modelix model server.
 *
 * @property sourceModel the source [SModel] of the Model Import.
 * @property targetModel the target [SModel] of the Model Import.
 * @property isResolved true, if the Model Import is resolved, i.e., is synchronized to the modelix model server.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class CloudResolvableModelImport(
    val sourceModel: SModel,
    val targetModel: SModel,
    var isResolved: Boolean = false,
)
