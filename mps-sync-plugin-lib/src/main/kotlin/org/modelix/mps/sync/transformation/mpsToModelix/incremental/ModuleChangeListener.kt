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
import mu.KotlinLogging
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SDependency
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleListener
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.getNode
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.modelix.util.nodeIdAsLong
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.mps.util.descriptorSuffix
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
     * Synchronizes an [org.jetbrains.mps.openapi.module.SModule] and its related elements (e.g. dependencies, imports)
     * to [org.modelix.model.api.INode]s on the model server.
     */
    private val moduleSynchronizer = ModuleSynchronizer(branch, serviceLocator)

    /**
     * Synchronizes an [org.jetbrains.mps.openapi.model.SModel] and its related elements (e.g. dependencies, imports)
     * to [org.modelix.model.api.INode]s on the model server.
     */
    private val modelSynchronizer = ModelSynchronizer(branch, serviceLocator = serviceLocator)

    /**
     * Synchronizes an [org.jetbrains.mps.openapi.model.SNode] to an [org.modelix.model.api.INode] on the model server.
     */
    private val nodeSynchronizer = NodeSynchronizer(branch, serviceLocator = serviceLocator)

    private val moduleChangeSyncInProgress = synchronizedLinkedHashSet<SModule>()

    override fun modelAdded(module: SModule, model: SModel) {
        modelSynchronizer.addModelAndActivate(model as SModelBase)
    }

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

    override fun moduleChanged(module: SModule) {
        synchronized(module) {
            /*
             * in some cases MPS might call this method multiple times consecutively(e.g. when we add the new
             * dependency), and we want to avoid breaking an ongoing synchronizations.
             */
            if (moduleChangeSyncInProgress.contains(module)) {
                return
            }
            moduleChangeSyncInProgress.add(module)
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

    override fun dependencyAdded(module: SModule, dependency: SDependency) {
        // handled by moduleChanged, because this method is never called
    }

    override fun dependencyRemoved(module: SModule, dependency: SDependency) {
        // handled by moduleChanged, because this method is never called
    }

    override fun modelRenamed(module: SModule, model: SModel, reference: SModelReference) {
        // duplicate of SModelListener.modelRenamed
    }

    override fun languageAdded(module: SModule, language: SLanguage) {}
    override fun languageRemoved(module: SModule, language: SLanguage) {}
    override fun beforeModelRemoved(module: SModule, model: SModel) {}
    override fun beforeModelRenamed(module: SModule, model: SModel, reference: SModelReference) {}

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
                        moduleChangeSyncInProgress.remove(module)
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

    private fun removeModuleFromSyncInProgressAndRethrow(module: SModule, throwable: Throwable?) {
        moduleChangeSyncInProgress.remove(module)
        throwable?.let {
            val exception = MpsToModelixSynchronizationException(it.message ?: pleaseCheckLogs, it)
            notifier.notifyAndLogError(exception.message, exception, logger)
            throw it
        }
    }
}
