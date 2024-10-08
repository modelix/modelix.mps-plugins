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

package org.modelix.mps.sync.transformation.modelixToMps.initial

import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.modelix.util.isModule
import org.modelix.mps.sync.modelix.util.nodeIdAsLong
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import java.util.concurrent.CompletableFuture

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ITreeToSTreeTransformer(
    private val branch: IBranch,
    mpsLanguageRepository: MPSLanguageRepository,
    serviceLocator: ServiceLocator,
) {

    /**
     * The task queue of the sync plugin.
     */
    private val syncQueue = serviceLocator.syncQueue
    private val moduleTransformer = ModuleTransformer(branch, serviceLocator, mpsLanguageRepository)

    fun transform(moduleId: String): Iterable<IBinding> {
        val result = syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ), SyncDirection.NONE) {
            val moduleNode = branch.getRootNode().allChildren.firstOrNull {
                it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) == moduleId
            }
            requireNotNull(moduleNode) { "Module node with ID '$moduleId' is not found on the root level." }
            require(moduleNode.isModule()) { "Transformation entry point (Node $moduleNode) must be a Module." }
            val entryNodeId = moduleNode.nodeIdAsLong()
            moduleTransformer.transformToModuleCompletely(entryNodeId, true).getResult()
        }

        @Suppress("UNCHECKED_CAST")
        val cfResult = result.getResult().get() as CompletableFuture<Iterable<IBinding>>
        return cfResult.get()
    }
}
