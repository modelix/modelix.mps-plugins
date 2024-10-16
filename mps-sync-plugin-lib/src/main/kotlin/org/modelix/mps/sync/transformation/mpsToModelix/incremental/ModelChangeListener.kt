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
import jetbrains.mps.smodel.event.SModelChildEvent
import jetbrains.mps.smodel.event.SModelDevKitEvent
import jetbrains.mps.smodel.event.SModelImportEvent
import jetbrains.mps.smodel.event.SModelLanguageEvent
import jetbrains.mps.smodel.event.SModelListener
import jetbrains.mps.smodel.event.SModelListener.SModelListenerPriority.CLIENT
import jetbrains.mps.smodel.event.SModelPropertyEvent
import jetbrains.mps.smodel.event.SModelReferenceEvent
import jetbrains.mps.smodel.event.SModelRenamedEvent
import jetbrains.mps.smodel.event.SModelRootEvent
import jetbrains.mps.smodel.loading.ModelLoadingState
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeChangeListener
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.mps.services.ProjectLifecycleTracker
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.tasks.ContinuableSyncTask
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModelSynchronizer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.NodeSynchronizer

/**
 * The change listener that is called, when a change on in an [SModel] in MPS occurred. This change will be played onto
 * the model server MPS in a way that the MPS elements are transformed to modelix elements by the corresponding
 * transformer methods.
 *
 * @param branch the modelix branch we are connected to.
 * @param serviceLocator a collector class to simplify injecting the commonly used services in the sync plugin.
 *
 * @property binding the active [ModelBinding] of the [SModel] to which this change listener belongs.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelChangeListener(
    private val binding: ModelBinding,
    branch: IBranch,
    serviceLocator: ServiceLocator,
) : SModelListener {

    /**
     * Tracks the active [Project]'s lifecycle.
     */
    private val projectLifecycleTracker = serviceLocator.projectLifecycleTracker

    /**
     * Synchronizes an [SModel] and its related elements (e.g. dependencies, imports) [INode]s on the model server.
     */
    private val modelSynchronizer = ModelSynchronizer(branch, serviceLocator = serviceLocator)

    /**
     * Synchronizes an [SNode] to an [INode] on the model server.
     */
    private val nodeSynchronizer = NodeSynchronizer(branch, serviceLocator = serviceLocator)

    /**
     * Handles a Model Import added event. The added Model Import should be synced to the model server.
     *
     * @param event contains the Model Import that has to be synced to the model server.
     *
     * @see [ModelSynchronizer.addModelImport].
     * @see [SModelListener.importAdded].
     */
    override fun importAdded(event: SModelImportEvent) {
        modelSynchronizer.addModelImport(event.model, event.modelUID)
            .continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) { resolveModelImports().getResult() }
    }

    /**
     * Handles a Model Import removed event. The removed Model Import should be deleted from the model server as well.
     *
     * @param event contains the Model Import that has to be removed from the model server.
     *
     * @see [NodeSynchronizer.removeNode].
     * @see [SModelListener.importRemoved].
     */
    override fun importRemoved(event: SModelImportEvent) {
        nodeSynchronizer.removeNode(
            parentNodeIdProducer = { it[event.model]!! },
            childNodeIdProducer = { it[event.model, event.modelUID]!! },
        )
    }

    /**
     * Handles a Language Dependency added event. The added Language Dependency should be synced to the model server.
     *
     * @param event contains the Language Dependency that has to be synced to the model server.
     *
     * @see [ModelSynchronizer.addLanguageDependency].
     * @see [SModelListener.languageAdded].
     */
    override fun languageAdded(event: SModelLanguageEvent) {
        modelSynchronizer.addLanguageDependency(event.model, event.eventLanguage)
    }

    /**
     * Handles a Language Dependency removed event. The removed Language Dependency should be deleted from the model
     * server as well.
     *
     * @param event contains the Language Dependency that has to be removed from the model server.
     *
     * @see [NodeSynchronizer.removeNode].
     * @see [SModelListener.languageRemoved].
     */
    override fun languageRemoved(event: SModelLanguageEvent) {
        nodeSynchronizer.removeNode(
            parentNodeIdProducer = { it[event.model]!! },
            childNodeIdProducer = { it[event.model, event.eventLanguage.sourceModuleReference]!! },
        )
    }

    /**
     * Handles a DevKit Dependency added event. The added DevKit Dependency should be synced to the model server.
     *
     * @param event contains the DevKit Dependency that has to be synced to the model server.
     *
     * @see [ModelSynchronizer.addLanguageDependency].
     * @see [SModelListener.devkitAdded].
     */
    override fun devkitAdded(event: SModelDevKitEvent) {
        modelSynchronizer.addDevKitDependency(event.model, event.devkitNamespace)
    }

    /**
     * Handles a DevKit Dependency removed event. The removed DevKit Dependency should be deleted from the model server
     * as well.
     *
     * @param event contains the Language Dependency that has to be removed from the model server.
     *
     * @see [NodeSynchronizer.removeNode].
     * @see [SModelListener.devkitRemoved].
     */
    override fun devkitRemoved(event: SModelDevKitEvent) {
        nodeSynchronizer.removeNode(
            parentNodeIdProducer = { it[event.model]!! },
            childNodeIdProducer = { it[event.model, event.devkitNamespace]!! },
        )
    }

    /**
     * Handles a model renamed event. The model has to be renamed on the model server as well.
     *
     * @param event contains the [SModel] that has to be renamed on the model server.
     *
     * @see [NodeSynchronizer.setProperty].
     * @see [SModelListener.modelRenamed].
     */
    override fun modelRenamed(event: SModelRenamedEvent) {
        nodeSynchronizer.setProperty(
            BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name,
            event.newName,
        ) { it[event.model]!! }
    }

    /**
     * Handles the before model disposed event. This method is called, if the [model] is about to be disposed, therefore
     * we have to do some precautionary clean-up activities.
     *
     * In our case, we deactivate the [binding] and remove the model from the model server iff the active [Project] is
     * not closing. That's because the user removed the [model] from the [Project], thus we have to remove it on the
     * model server too.
     *
     * @param model the [SModel] that will be disposed in MPS.
     *
     * @see [ProjectLifecycleTracker.projectClosing].
     * @see [ModelBinding.deactivate].
     * @see [SModelListener.beforeModelDisposed].
     */
    override fun beforeModelDisposed(model: SModel) {
        if (!projectLifecycleTracker.projectClosing) {
            binding.deactivate(removeFromServer = true)
        }
    }

    /**
     * @return [CLIENT].
     *
     * @see [SModelListener.getPriority].
     */
    override fun getPriority() = CLIENT

    /**
     * Does nothing, because it is a duplicate of [SNodeChangeListener.nodeAdded].
     *
     * @see [SModelListener.rootAdded].
     */
    @Deprecated("Deprecated in Java")
    override fun rootAdded(event: SModelRootEvent) {
    }

    /**
     * Does nothing, because it is a duplicate of [SNodeChangeListener.nodeRemoved].
     *
     * @see [SModelListener.rootRemoved].
     */
    @Deprecated("Deprecated in Java")
    override fun rootRemoved(event: SModelRootEvent) {
    }

    /**
     * Does nothing, because it is a duplicate of [SNodeChangeListener.propertyChanged].
     *
     * @see [SModelListener.propertyChanged].
     */
    override fun propertyChanged(event: SModelPropertyEvent) {}

    /**
     * Does nothing, because it is a duplicate of [SNodeChangeListener.nodeAdded].
     *
     * @see [SModelListener.childAdded].
     */
    override fun childAdded(event: SModelChildEvent) {}

    /**
     * Does nothing, because it is a duplicate of [SNodeChangeListener.nodeRemoved].
     *
     * @see [SModelListener.childRemoved].
     */
    override fun childRemoved(event: SModelChildEvent) {}

    /**
     * Does nothing, because it is a duplicate of [SNodeChangeListener.referenceChanged].
     *
     * @see [SModelListener.referenceAdded].
     */
    override fun referenceAdded(event: SModelReferenceEvent) {}

    /**
     * Does nothing, because it is a duplicate of [SNodeChangeListener.referenceChanged].
     *
     * @see [SModelListener.referenceRemoved].
     */
    override fun referenceRemoved(event: SModelReferenceEvent) {}

    /**
     * Does nothing.
     *
     * @see [SModelListener.beforeChildRemoved].
     */
    override fun beforeChildRemoved(event: SModelChildEvent) {}

    /**
     * Does nothing.
     *
     * @see [SModelListener.beforeRootRemoved].
     */
    override fun beforeRootRemoved(event: SModelRootEvent) {}

    /**
     * Does nothing.
     *
     * @see [SModelListener.beforeModelRenamed].
     */
    override fun beforeModelRenamed(event: SModelRenamedEvent) {}

    /**
     * Does nothing.
     *
     * @see [SModelListener.modelSaved].
     */
    override fun modelSaved(model: SModel) {}

    /**
     * Does nothing.
     *
     * @see [SModelListener.modelLoadingStateChanged].
     */
    override fun modelLoadingStateChanged(model: SModel?, state: ModelLoadingState) {}

    /**
     * Resolves the Model Imports in [modelSynchronizer].
     *
     * @see [ModelSynchronizer.resolveModelImportsInTask].
     *
     * @return the [ContinuableSyncTask] handle to append a new sync task after this one is completed.
     */
    fun resolveModelImports() = modelSynchronizer.resolveModelImportsInTask()
}
