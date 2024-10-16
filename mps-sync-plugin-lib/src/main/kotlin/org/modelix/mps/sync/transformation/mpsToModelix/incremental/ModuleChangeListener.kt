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

package org.modelix.mps.sync.transformation.mpsToModelix.incremental

import com.intellij.openapi.project.Project
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.extapi.model.SModelDescriptorStub
import jetbrains.mps.smodel.event.SModelListener
import mu.KotlinLogging
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SDependency
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleListener
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.getNode
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.modelix.util.nodeIdAsLong
import org.modelix.mps.sync.mps.notifications.WrappedNotifier
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.mps.util.descriptorSuffix
import org.modelix.mps.sync.mps.util.isDescriptorModel
import org.modelix.mps.sync.tasks.ContinuableSyncTask
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncTaskAction
import org.modelix.mps.sync.transformation.exceptions.MpsToModelixSynchronizationException
import org.modelix.mps.sync.transformation.exceptions.pleaseCheckLogs
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModelSynchronizer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModuleSynchronizer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.NodeSynchronizer
import org.modelix.mps.sync.util.completeWithDefault
import org.modelix.mps.sync.util.synchronizedLinkedHashSet
import org.modelix.mps.sync.util.waitForCompletionOfEach
import org.modelix.mps.sync.util.waitForCompletionOfEachTask
import java.util.concurrent.CompletableFuture

/**
 * The change listener that is called, when a change on in an [SModule] in MPS occurred. This change will be played onto
 * the model server MPS in a way that the MPS elements are transformed to modelix elements by the corresponding
 * transformer methods.
 *
 * @param serviceLocator a collector class to simplify injecting the commonly used services in the sync plugin.
 *
 * @property branch the modelix branch we are connected to.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModuleChangeListener(private val branch: IBranch, serviceLocator: ServiceLocator) : SModuleListener {

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
     * Tracks the active [Project]'s lifecycle.
     */
    private val projectLifecycleTracker = serviceLocator.projectLifecycleTracker

    /**
     * Synchronizes an [SModule] and its related elements (e.g. dependencies, imports) to [INode]s on the model server.
     */
    private val moduleSynchronizer = ModuleSynchronizer(branch, serviceLocator)

    /**
     * Synchronizes an [SModel] and its related elements (e.g. dependencies, imports) to [INode]s on the model server.
     */
    private val modelSynchronizer = ModelSynchronizer(branch, serviceLocator = serviceLocator)

    /**
     * Synchronizes an [SNode] to an [INode] on the model server.
     */
    private val nodeSynchronizer = NodeSynchronizer(branch, serviceLocator = serviceLocator)

    /**
     * A barrier to block consecutive calls of [moduleChanged] for the same [SModule].
     */
    private val moduleChangeSyncInProgress = synchronizedLinkedHashSet<SModuleReference>()

    /**
     * Handles a model added event. The added [model] should be synced to the model server.
     *
     * @param module the parent [SModule] of the model.
     * @param model the newly added [SModel].
     *
     * @see [ModelSynchronizer.addModelAndActivate].
     * @see [SModuleListener.modelAdded].
     */
    override fun modelAdded(module: SModule, model: SModel) {
        modelSynchronizer.addModelAndActivate(model as SModelBase)
    }

    /**
     * Handles a model removed event. The removed model, referred to by the [reference], must be removed from the
     * model server.
     *
     * If [reference] identifies a Descriptor Model,see [isDescriptorModel], then this method does nothing. That's
     * because Descriptor Models are not synced to the model server, therefore they are not removed either.
     *
     * @param module the old parent [SModule] of the model.
     * @param reference a reference to the [SModel] that was removed.
     *
     * @see [isDescriptorModel].
     * @see [NodeSynchronizer.removeNode].
     * @see [SModuleListener.modelRemoved].
     */
    override fun modelRemoved(module: SModule, reference: SModelReference) {
        if (projectLifecycleTracker.projectClosing) {
            return
        }

        // We do not track changes in descriptor models. See ModelTransformer.isDescriptorModel()
        val isDescriptorModel = reference.modelName.endsWith(descriptorSuffix)
        if (isDescriptorModel) {
            return
        }

        val modelId = reference.modelId
        val binding = bindingsRegistry.getModelBinding(modelId)
        // if binding is not found, it means the model should be removed (see ModelBinding's deactivate method)
        if (binding == null) {
            nodeSynchronizer.removeNode(
                parentNodeIdProducer = { it[module]!! },
                childNodeIdProducer = { it[modelId]!! },
            )
        }
    }

    /**
     * Handles a module changed event. This event occurs if the [module]'s dependencies changed (a new one was added,
     * an old one removed), or the [module] was renamed.
     *
     * If a new Module Dependency is added, then the dependent [SModule] is also transitively synced to modelix.
     * However, if the Module Dependency is removed, then the dependent is [SModule] is not removed from modelix.
     *
     * @param [module] the [SModule] that changed.
     *
     * @see [NodeSynchronizer.setProperty].
     * @see [NodeSynchronizer.removeNode].
     * @see [ModuleSynchronizer.addDependency].
     * @see [ModuleChangeListener.moduleChanged].
     */
    override fun moduleChanged(module: SModule) {
        /*
         * in some cases MPS might call this method multiple times consecutively(e.g. when we add the new
         * dependency), and we want to avoid breaking an ongoing synchronizations.
         */
        val moduleReference = module.moduleReference
        synchronized(moduleReference) {
            if (moduleChangeSyncInProgress.contains(moduleReference)) {
                return
            }
            moduleChangeSyncInProgress.add(moduleReference)
        }

        syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            errorHandlerWrapper(it, module) {
                // check if name is the same
                val iModuleNodeId = nodeMap[module]!!
                val iModule = branch.getNode(iModuleNodeId)
                val nameProperty = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name
                val iName = iModule.getPropertyValue(nameProperty)
                val actualName = module.moduleName!!

                if (actualName != iName) {
                    nodeSynchronizer.setProperty(
                        nameProperty,
                        actualName,
                        sourceNodeIdProducer = { iModuleNodeId },
                    ).getResult()
                } else {
                    null
                }
            }
        }.continueWith(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            errorHandlerWrapper(it, module) {
                // add new dependencies
                val iModuleNodeId = nodeMap[module]!!
                val iModule = branch.getNode(iModuleNodeId)
                val lastKnownDependencies =
                    iModule.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies)
                val actualDependencies = module.declaredDependencies

                val addedDependencies = actualDependencies.filter { sDependency ->
                    lastKnownDependencies.none { dependencyINode ->
                        val targetModuleId = sDependency.targetModule.moduleId
                        ModuleTransformer.getTargetModuleIdFromModuleDependency(dependencyINode) == targetModuleId
                    }
                }
                addedDependencies.waitForCompletionOfEachTask(futuresWaitQueue, collectResults = true) { dependency ->
                    moduleSynchronizer.addDependency(
                        module,
                        dependency,
                    )
                }
            }
        }.continueWith(linkedSetOf(SyncLock.MODELIX_WRITE, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            // resolve cross-model references that we might have created by adding new dependencies
            errorHandlerWrapper(it, module) { bindings ->
                moduleSynchronizer.resolveCrossModelReferences()
                CompletableFuture.completedFuture(bindings)
            }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
            // activate the bindings of the newly added dependencies
            errorHandlerWrapper(it, module) { bindings ->
                @Suppress("UNCHECKED_CAST")
                (bindings as Iterable<Iterable<IBinding>>).flatten().forEach(IBinding::activate)
            }
        }.continueWith(linkedSetOf(SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            // resolve model imports (that had not been resolved, because the corresponding module/model were not uploaded yet)
            errorHandlerWrapper(it, module) {
                module.models.waitForCompletionOfEach(futuresWaitQueue) { model ->
                    if (model is SModelDescriptorStub && model.modelListeners.isNotEmpty()) {
                        model.modelListeners.waitForCompletionOfEach(futuresWaitQueue) { listener ->
                            if (listener is ModelChangeListener) {
                                listener.resolveModelImports().getResult()
                            } else {
                                CompletableFuture<Any?>().completeWithDefault()
                            }
                        }
                    } else {
                        CompletableFuture<Any?>().completeWithDefault()
                    }
                }
            }
        }.continueWith(linkedSetOf(SyncLock.MODELIX_READ, SyncLock.MPS_READ), SyncDirection.MPS_TO_MODELIX) {
            try {
                // remove deleted dependencies
                val iModuleNodeId = nodeMap[module]!!
                val iModule = branch.getNode(iModuleNodeId)
                val lastKnownDependencies =
                    iModule.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies)
                val actualDependencies = module.declaredDependencies

                val removedDependencies = lastKnownDependencies.filter { dependencyINode ->
                    val targetModuleIdAccordingToModelix =
                        ModuleTransformer.getTargetModuleIdFromModuleDependency(dependencyINode)
                    actualDependencies.none { sDependency ->
                        targetModuleIdAccordingToModelix == sDependency.targetModule.moduleId
                    }
                }
                removedDependencies.waitForCompletionOfEachTask(futuresWaitQueue) { dependencyINode ->
                    nodeSynchronizer.removeNode(
                        parentNodeIdProducer = { it[module]!! },
                        childNodeIdProducer = { dependencyINode.nodeIdAsLong() },
                    )
                }.handle { result, throwable ->
                    removeModuleFromSyncInProgressAndRethrow(module, throwable)
                    result
                }
            } catch (t: Throwable) {
                removeModuleFromSyncInProgressAndRethrow(module, t)
            }
        }
    }

    /**
     * Does nothing. This case is already handled by [moduleChanged], because this method is never called.
     *
     * @see [moduleChanged].
     * @see [SModuleListener.dependencyAdded].
     */
    override fun dependencyAdded(module: SModule, dependency: SDependency) {}

    /**
     * Does nothing. This case is already handled by [moduleChanged], because this method is never called.
     *
     * @see [moduleChanged].
     * @see [SModuleListener.dependencyRemoved].
     */
    override fun dependencyRemoved(module: SModule, dependency: SDependency) {}

    /**
     * Does nothing, because it is a duplicate of [SModelListener.modelRenamed].
     *
     * @see [SModuleListener.modelRenamed].
     */
    override fun modelRenamed(module: SModule, model: SModel, reference: SModelReference) {}

    /**
     * Does nothing.
     *
     * @see [SModuleListener.languageAdded].
     */
    override fun languageAdded(module: SModule, language: SLanguage) {}

    /**
     * Does nothing.
     *
     * @see [SModuleListener.languageRemoved].
     */
    override fun languageRemoved(module: SModule, language: SLanguage) {}

    /**
     * Does nothing.
     *
     * @see [SModuleListener.beforeModelRemoved].
     */
    override fun beforeModelRemoved(module: SModule, model: SModel) {}

    /**
     * Does nothing.
     *
     * @see [SModuleListener.beforeModelRenamed].
     */
    override fun beforeModelRenamed(module: SModule, model: SModel, reference: SModelReference) {}

    /**
     * A circuit breaker if any exception occurs while executing [func] with the [input]. If an exception occurred,
     * then we have to remove the [module] from [moduleChangeSyncInProgress], notify the user about it, and break the
     * chain of [ContinuableSyncTask] coming after this task.
     *
     * @param input the parameter of [func].
     * @param module the [SModule] that we were syncing.
     * @param func the action that we want to execute with the [input] parameter.
     *
     * @throws [Throwable] if an error occurred while executing [func] with the [input] parameter.
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
    @Throws(Throwable::class)
    private fun errorHandlerWrapper(
        input: Any?,
        module: SModule,
        func: SyncTaskAction,
    ): CompletableFuture<Any?> {
        try {
            val continuation = CompletableFuture<Any?>()

            val result = func(input)
            if (result is CompletableFuture<*>) {
                result.whenComplete { taskResult, throwable ->
                    if (throwable != null) {
                        moduleChangeSyncInProgress.remove(module.moduleReference)
                        continuation.completeExceptionally(throwable)
                    } else {
                        continuation.complete(taskResult)
                    }
                }
            } else {
                continuation.complete(result)
            }

            return continuation
        } catch (t: Throwable) {
            removeModuleFromSyncInProgressAndRethrow(module, t)
            // should never reach beyond this point, because the method above rethrows the throwable anyway
            throw t
        }
    }

    /**
     * Removes the [module] from the [moduleChangeSyncInProgress] barrier and then logs and rethrows [throwable] after
     * wrapping it into an [MpsToModelixSynchronizationException] (unless the [throwable] is null).
     *
     * @param module the [SModule] that we were syncing.
     * @param throwable the cause of the error the occurred.
     *
     * @throws rethrows [throwable] if it is not null.
     *
     * @see [moduleChangeSyncInProgress].
     * @see [MpsToModelixSynchronizationException].
     * @see [WrappedNotifier.notifyAndLogError].
     */
    @Throws(Throwable::class)
    private fun removeModuleFromSyncInProgressAndRethrow(module: SModule, throwable: Throwable?) {
        moduleChangeSyncInProgress.remove(module.moduleReference)
        throwable?.let {
            val exception = MpsToModelixSynchronizationException(it.message ?: pleaseCheckLogs, it)
            notifier.notifyAndLogError(exception.message, exception, logger)
            throw it
        }
    }
}
