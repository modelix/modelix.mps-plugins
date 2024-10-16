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

import jetbrains.mps.model.ModelDeleteHelper
import jetbrains.mps.module.ModuleDeleteHelper
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.structure.modules.ModuleReference
import jetbrains.mps.project.structure.modules.SolutionDescriptor
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.uuid
import org.modelix.model.api.ChildLinkFromName
import org.modelix.model.api.IBranch
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.api.getNode
import org.modelix.model.api.getRootNode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.EmptyBinding
import org.modelix.mps.sync.bindings.ModuleBinding
import org.modelix.mps.sync.modelix.util.ModuleDependencyConstants
import org.modelix.mps.sync.modelix.util.nodeIdAsLong
import org.modelix.mps.sync.mps.factories.SolutionProducer
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.tasks.ContinuableSyncTask
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.transformation.cache.ModuleWithModuleReference
import org.modelix.mps.sync.transformation.exceptions.ModelixToMpsSynchronizationException
import org.modelix.mps.sync.util.BooleanUtil
import org.modelix.mps.sync.util.bindTo
import org.modelix.mps.sync.util.waitForCompletionOfEachTask
import java.text.ParseException
import java.util.concurrent.CompletableFuture

/**
 * Transforms a modelix [INode] that represents an MPS Module to the corresponding [SModule], or to concepts related to
 * that (e.g., Module Dependencies). Besides, it transforms the changes that occurred to its properties or references
 * on the modelix side to the corresponding changes on the MPS side.
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
class ModuleTransformer(
    private val branch: IBranch,
    private val serviceLocator: ServiceLocator,
    mpsLanguageRepository: MPSLanguageRepository,
) {

    companion object {

        /**
         * @param moduleDependency the modelix node that represents a Module Dependency.
         *
         * @return an [SModuleId] that is created from the [uuid] field of the modelix node
         */
        fun getTargetModuleIdFromModuleDependency(moduleDependency: INode): SModuleId {
            val uuid = moduleDependency.getPropertyValue(uuid)!!
            return PersistenceFacade.getInstance().createModuleId(uuid)
        }
    }

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
     * The [MPSProject] that is open in the active MPS window.
     */
    private val mpsProject = serviceLocator.mpsProject

    /**
     * The active [SRepository] to access the [SModel]s and [SModule]s in MPS.
     */
    private val mpsRepository = serviceLocator.mpsRepository

    private val solutionProducer = SolutionProducer(mpsProject)

    /**
     * The MPS Model to modelix node transformer.
     */
    private val modelTransformer = ModelTransformer(branch, serviceLocator, mpsLanguageRepository)

    /**
     * Transforms a modelix node, identified by its [nodeId], to an [SModule] completely (i.e., all Module Dependencies,
     * contained Models - see [ModelTransformer.transformToModelCompletely]).
     *
     * The transformed elements are automatically added to the project in MPS and are not returned by the transformation
     * methods.
     *
     * @param nodeId the identifier of the modelix node that represents the [SModule].
     * @param isTransformationStartingModule if true, then the cross-model references will be resolved after the
     * transformation of the dependent models.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed. The result of
     * this task is a [Set] of [IBinding]s. One of them is the [ModuleBinding] that represents the synchronized Module.
     * The other [IBinding]s were created by synchronizing the Module Dependency target modules (and their models).
     */
    fun transformToModuleCompletely(nodeId: Long, isTransformationStartingModule: Boolean = false) =
        transformToModule(nodeId, true)
            .continueWith(linkedSetOf(SyncLock.MODELIX_READ), SyncDirection.MODELIX_TO_MPS) { dependencyBindings ->
                // transform models
                val module = branch.getNode(nodeId)
                val modelBindingsFuture = module.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models)
                    .waitForCompletionOfEachTask(futuresWaitQueue, collectResults = true) {
                        modelTransformer.transformToModelCompletely(it.nodeIdAsLong())
                    }

                // join the newly added model bindings with the existing bindings
                val collectedBindingsFuture = CompletableFuture<Any?>()
                futuresWaitQueue.add(
                    collectedBindingsFuture,
                    setOf(modelBindingsFuture, CompletableFuture.completedFuture(dependencyBindings)),
                    collectResults = true,
                )
                collectedBindingsFuture
            }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) { unflattenedBindings ->
                @Suppress("UNCHECKED_CAST")
                (unflattenedBindings as Iterable<Iterable<IBinding>>).flatten()
            }.continueWith(linkedSetOf(SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) { flattenedBindings ->
                // resolve references only after all dependent (and contained) modules and models have been transformed
                if (isTransformationStartingModule) {
                    // resolve cross-model references (and node references)
                    modelTransformer.resolveCrossModelReferences(mpsRepository)
                }
                flattenedBindings
            }.continueWith(linkedSetOf(SyncLock.MPS_READ), SyncDirection.MODELIX_TO_MPS) { dependencyAndModelBindings ->
                // register binding
                val iNode = branch.getNode(nodeId)
                val module = nodeMap.getModule(iNode.nodeIdAsLong()) as AbstractModule
                val moduleBinding = ModuleBinding(module, branch, serviceLocator)
                bindingsRegistry.addModuleBinding(moduleBinding)

                val bindings = mutableSetOf<IBinding>()
                @Suppress("UNCHECKED_CAST")
                bindings.addAll(dependencyAndModelBindings as Iterable<IBinding>)
                bindings.add(moduleBinding)
                bindings
            }

    /**
     * Transforms a modelix node, identified by its [nodeId], to an [SModule] without transforming the contained models.
     * After that it creates and activates the module's [ModuleBinding].
     *
     * @param nodeId the identifier of the modelix node that represents the [SModule].
     * @param fetchTargetModule if true, then the target [SModule]s of the Module Dependencies (outgoing from this
     * source [SModule]) will be also transformed.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     *
     * @see [transformToModule]
     */
    fun transformToModuleAndActivate(nodeId: Long, fetchTargetModule: Boolean = false) =
        transformToModule(nodeId, fetchTargetModule)
            .continueWith(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
                val iNode = branch.getNode(nodeId)
                val module = nodeMap.getModule(iNode.nodeIdAsLong()) as AbstractModule
                val moduleBinding = ModuleBinding(module, branch, serviceLocator)
                bindingsRegistry.addModuleBinding(moduleBinding)
                moduleBinding.activate()
            }

    /**
     * Transforms a modelix node, identified by its [nodeId], to an [SModule] without transforming the contained models.
     *
     * The transformed elements are automatically added to the project in MPS and are not returned by the transformation
     * methods.
     *
     * @param nodeId the identifier of the modelix node that represents the [SModule].
     * @param fetchTargetModule if true, then the target [SModule]s of the Module Dependencies (outgoing from this
     * source [SModule]) will be also transformed.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed. The result of
     * this task is a [List] of [IBinding]s that were created by synchronizing the Module Dependency target module (and
     * its models). If the Module Dependency target was not synchronized then a [List] of [EmptyBinding]s is returned.
     */
    private fun transformToModule(nodeId: Long, fetchTargetModule: Boolean = false) =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
            val serializedId = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) ?: ""
            check(serializedId.isNotEmpty()) {
                val message = "Node ($iNode) cannot be transformed to Module, because its ID is empty."
                notifyAndLogError(message)
                message
            }

            val moduleId = PersistenceFacade.getInstance().createModuleId(serializedId)
            val name = iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
            checkNotNull(name) { "Module's ($iNode) name is null" }

            val sModule = solutionProducer.createOrGetModule(name, moduleId as ModuleId)
            nodeMap.put(sModule, iNode.nodeIdAsLong())

            // transform dependencies and collect its bindings (implicitly via the tasks' return value)
            iNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies)
                .waitForCompletionOfEachTask(futuresWaitQueue, collectResults = true) {
                    transformModuleDependency(it.nodeIdAsLong(), sModule, fetchTargetModule)
                }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) { unflattenedBindings ->
            @Suppress("UNCHECKED_CAST")
            (unflattenedBindings as Iterable<Iterable<IBinding>>).flatten()
        }

    /**
     * Transforms a modelix node, identified by its [nodeId], to a Module Dependency that is outgoing from the
     * [parentModule].
     *
     * The transformed elements are automatically added to the project in MPS and are not returned by the transformation
     * methods.
     *
     * @param nodeId the identifier of the modelix node that represents the [SModule].
     * @param parentModule the [SModule] that contains the outgoing Module Dependency.
     * @param fetchTargetModule if true, then the target [SModule]s of the Module Dependencies (outgoing from this
     * source [SModule]) will be also transformed.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed. The result of
     * this task is a [Set] of [IBinding]s that were created by synchronizing the Module Dependency target module (and
     * its models). If the Module Dependency target was not synchronized then a [Set] of [EmptyBinding]s is returned.
     */
    fun transformModuleDependency(
        nodeId: Long,
        parentModule: AbstractModule,
        fetchTargetModule: Boolean = false,
    ): ContinuableSyncTask =
        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE), SyncDirection.MODELIX_TO_MPS) {
            val iNode = branch.getNode(nodeId)
            val targetModuleId = getTargetModuleIdFromModuleDependency(iNode)
            val isTargetModuleReadOnly =
                "true" == iNode.getPropertyValue(ModuleDependencyConstants.MODULE_DEPENDENCY_IS_READ_ONLY_PROPERTY)

            // decide, if we have to transform the target Module first, before transforming the Module Dependency
            // however, if target module is read-only then we do not transform it (we expect it to exist in MPS)
            val future = CompletableFuture<Any?>()
            val targetModuleIsNotMapped = nodeMap.getModule(targetModuleId) == null
            if (!isTargetModuleReadOnly && targetModuleIsNotMapped && fetchTargetModule) {
                // find target module in modelix
                val targetModule = branch.getRootNode().getChildren(ChildLinkFromName("modules")).firstOrNull {
                    val serializedId = it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id)
                    serializedId?.let {
                        val moduleId = PersistenceFacade.getInstance().createModuleId(serializedId)
                        moduleId == targetModuleId
                    } ?: false
                }

                if (targetModule != null) {
                    transformToModuleCompletely(targetModule.nodeIdAsLong()).getResult().bindTo(future)
                } else {
                    /*
                     * Issue MODELIX-820:
                     * A manual quick-fix would be if the module is available on another model server and its ID is
                     * the same as what is used in the ModuleDependency, then just upload that module to this server
                     * and rerun the transformation.
                     * TODO (1) Show it as a suggestion to the user?
                     * TODO (2) As a final fallback, the user could remove the module dependency. In this case, we have
                     * to implement the corresponding feature (e.g., as an action by clicking on a button). Maybe this
                     * direction would be easier for the user and for us too.
                     */
                    val targetModuleName =
                        iNode.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
                    val message =
                        "ModuleDependency ($nodeId) that goes out from Module ${parentModule.moduleName} cannot be transformed, because its Target Module ($targetModuleName) is not found on the server."
                    notifyAndLogError(message)
                    future.completeExceptionally(NoSuchElementException(message))
                }
            } else {
                future.complete(setOf(EmptyBinding()))
            }

            future
        }.continueWith(
            linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_WRITE),
            SyncDirection.MODELIX_TO_MPS,
        ) { dependencyBinding ->
            val iNode = branch.getNode(nodeId)
            val targetModuleId = getTargetModuleIdFromModuleDependency(iNode)

            if (parentModule.moduleId != targetModuleId) {
                val moduleName = iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.name)
                val moduleReference = ModuleReference(moduleName, targetModuleId)
                val reexport = (
                    iNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.reexport)
                        ?: "false"
                    ).toBoolean()
                parentModule.addDependency(moduleReference, reexport)

                nodeMap.put(parentModule, moduleReference, iNode.nodeIdAsLong())
            } else {
                // do not transform self-dependencies
                logger.warn { "Self-dependency of Module ($parentModule) is ignored." }
            }

            dependencyBinding
        }

    /**
     * Handles a property change event in modelix, that should be played into MPS. This event occurs if a property of
     * a modelix node changed, and this property represents an [SModule] in MPS.
     *
     * @param role the name or UID of the property.
     * @param nodeId the identifier of the modelix node that represents the [SModule] and whose property changed.
     * @param sModule the [SModule] whose property changed.
     * @param newValue the new value of the property.
     * @param usesRoleIds shows if [role] is a human-readable name or a UID.
     */
    fun modulePropertyChanged(role: String, nodeId: Long, sModule: SModule, newValue: String?, usesRoleIds: Boolean) {
        val moduleId = sModule.moduleId
        if (sModule !is AbstractModule) {
            val message =
                "SModule ($moduleId) is not an AbstractModule, therefore its $role property cannot be changed. Corresponding Node ID is $nodeId."
            notifyAndLogError(message)
            return
        }

        val nameProperty = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name
        val isNameProperty = if (usesRoleIds) {
            role == nameProperty.getUID()
        } else {
            role == nameProperty.getSimpleName()
        }

        val moduleVersionProperty = BuiltinLanguages.MPSRepositoryConcepts.Module.moduleVersion
        val isModuleVersionProperty = if (usesRoleIds) {
            role == moduleVersionProperty.getUID()
        } else {
            role == moduleVersionProperty.getSimpleName()
        }

        if (isNameProperty) {
            val oldValue = sModule.moduleName
            if (oldValue != newValue) {
                if (newValue.isNullOrEmpty()) {
                    val message = "Name cannot be null or empty for Module $moduleId. Corresponding Node ID is $nodeId."
                    notifyAndLogError(message)
                    return
                }

                /*
                 * Additional TODO in MODELIX-726:
                 * MPS 2022.2.2 introduced a breaking API change to the Renamer class. The change is not backwards
                 * compatible so we have to find a common way for renaming module that works for MPS versions before
                 * and after this breaking change.
                 *
                 * val activeProject = ActiveMpsProjectInjector.activeMpsProject as Project
                 * Renamer(activeProject).renameModule(sModule, newValue)
                 */
            }
        } else if (isModuleVersionProperty) {
            try {
                val newVersion = newValue?.toInt() ?: return
                val oldVersion = sModule.moduleVersion
                if (oldVersion != newVersion) {
                    sModule.moduleVersion = newVersion
                }
            } catch (ex: NumberFormatException) {
                val message =
                    "New module version ($newValue) of SModule ($moduleId) is not an integer, therefore it cannot be set in MPS. Corresponding Node ID is $nodeId."
                notifyAndLogError(message)
            }
        } else if (role == BuiltinLanguages.MPSRepositoryConcepts.Module.compileInMPS.getSimpleName()) {
            try {
                val newCompileInMPS = newValue?.let { BooleanUtil.toBooleanStrict(it) } ?: return
                val moduleDescriptor = sModule.moduleDescriptor ?: return
                val oldCompileInMPS = moduleDescriptor.compileInMPS
                if (oldCompileInMPS != newCompileInMPS) {
                    if (moduleDescriptor !is SolutionDescriptor) {
                        val message =
                            "Module ($moduleId)'s descriptor is not a SolutionDescriptor, therefore compileInMPS will not be (un)set. Corresponding Node ID is $nodeId."
                        notifyAndLogError(message)
                        return
                    }
                    moduleDescriptor.compileInMPS = newCompileInMPS
                }
            } catch (ex: ParseException) {
                val message =
                    "New compileInMPS ($newValue) property of SModule ($moduleId) is not a strict boolean, therefore it cannot be set. Corresponding Node ID is $nodeId."
                notifyAndLogError(message)
            }
        } else {
            val message =
                "Role $role is unknown for concept Module. Therefore the property is not set in MPS from Node $nodeId."
            notifyAndLogError(message)
        }
    }

    /**
     * Handles a node removed event in modelix. If a node that represents an [SModule] is deleted in modelix, then it
     * should be also removed in MPS.
     *
     * @param sModule the [SModule] that should be removed in MPS.
     * @param nodeId the identifier of the modelix node that represents the [SModule] that was deleted.
     */
    fun moduleDeleted(sModule: SModule, nodeId: Long) {
        sModule.models.forEach { model ->
            val modelNodeId = nodeMap[model]
            ModelDeleteHelper(model).delete()
            modelNodeId?.let { nodeMap.remove(it) }
        }
        ModuleDeleteHelper(mpsProject).deleteModules(listOf(sModule), false, true)
        nodeMap.remove(nodeId)
    }

    /**
     * Handles a node removed event in modelix. If a node that represents a Module Dependency is deleted in modelix,
     * then it should be also removed in modelix.
     *
     * @param moduleWithModuleReference represents a Module Dependency in MPS that should be deleted.
     * @param nodeId the identifier of the modelix node that represents the Module Dependency.
     */
    fun outgoingModuleReferenceFromModuleDeleted(moduleWithModuleReference: ModuleWithModuleReference, nodeId: Long) {
        val sourceModule = moduleWithModuleReference.sourceModuleReference.resolve(mpsRepository)
        if (sourceModule !is AbstractModule) {
            val message =
                "Source Module ($sourceModule) is not an AbstractModule, therefore the outgoing Module Dependency reference cannot be removed. Corresponding Node ID is $nodeId."
            notifyAndLogError(message)
            return
        }

        val targetModuleReference = moduleWithModuleReference.moduleReference
        val dependency =
            sourceModule.moduleDescriptor?.dependencies?.firstOrNull { it.moduleRef == targetModuleReference }
        if (dependency != null) {
            sourceModule.removeDependency(dependency)
            nodeMap.remove(moduleWithModuleReference)
        } else {
            val message =
                "Outgoing dependency $targetModuleReference from Module $sourceModule is not found, therefore it cannot be deleted. Corresponding Node ID is $nodeId."
            notifyAndLogError(message)
        }
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
}
