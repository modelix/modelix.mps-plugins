/*
 * Copyright (c) 2024.
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

package org.modelix.mps.sync.mps

import com.intellij.openapi.project.Project
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.module.SRepositoryListenerBase
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.transformation.mpsToModelix.initial.NodeSynchronizer

/**
 * A change listener to listen to [SRepository] events that are relevant for the MPS <-> modelix model server
 * synchronization.
 *
 * @param branch the branch we are conencted to on the model server.
 * @param serviceLocator a collector class to simplify injecting the commonly used services in the sync plugin.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class RepositoryChangeListener(branch: IBranch, serviceLocator: ServiceLocator) : SRepositoryListenerBase() {

    /**
     * Tracks the active [Project]'s lifecycle.
     */
    private val projectLifecycleTracker = serviceLocator.projectLifecycleTracker

    /**
     * The registry to store the [IBinding]s.
     */
    private val bindingsRegistry = serviceLocator.bindingsRegistry

    /**
     * Synchronizes an [SNode] to an [INode] on the model server.
     */
    private val nodeSynchronizer = NodeSynchronizer(branch, serviceLocator = serviceLocator)

    /**
     * Listens to the module removed event of MPS. If a module is removed, and the module is synchronized to the model
     * server, then we have to removed from the model server and deactivate its binding.
     *
     * @param module a reference to the module that is removed.
     *
     * @see [SRepositoryListenerBase.moduleRemoved].
     */
    override fun moduleRemoved(module: SModuleReference) {
        if (projectLifecycleTracker.projectClosing) {
            return
        }

        val binding = bindingsRegistry.getModuleBindings().find { it.module.moduleId == module.moduleId }
        if (binding != null) {
            nodeSynchronizer.removeNode(
                parentNodeIdProducer = { ITree.ROOT_ID },
                childNodeIdProducer = { it[module.moduleId]!! },
            )
            binding.deactivate(removeFromServer = true)
        }
    }
}
