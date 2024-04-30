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
import org.modelix.model.api.INode
import org.modelix.model.api.getRootNode
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.modelixToMps.transformers.ModuleTransformer
import org.modelix.mps.sync.util.isModule
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ITreeToSTreeTransformer(private val branch: IBranch, mpsLanguageRepository: MPSLanguageRepository) {

    private val moduleTransformer = ModuleTransformer(branch, mpsLanguageRepository)

    private val syncQueue = SyncQueue

    fun transform(moduleId: String): Iterable<IBinding> {
        val result = syncQueue.enqueue(linkedSetOf(SyncLock.MODELIX_READ), SyncDirection.NONE) {
            val result = AtomicReference<INode>()
            branch.runRead {
                val moduleNode = branch.getRootNode().allChildren.firstOrNull {
                    it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) == moduleId
                }
                requireNotNull(moduleNode) { "Module node with ID '$moduleId' is not found on the root level." }
                require(moduleNode.isModule()) { "Transformation entry point (Node $moduleNode) must be a Module." }
                result.set(moduleNode)
            }
            result.get()
        }.continueWith(linkedSetOf(SyncLock.MODELIX_READ), SyncDirection.NONE) {
            val entryNodeId = (it as INode).nodeIdAsLong()
            moduleTransformer.transformToModuleCompletely(entryNodeId, true).getResult()
        }

        @Suppress("UNCHECKED_CAST")
        val cfResult = result.getResult().get() as CompletableFuture<Iterable<IBinding>>
        return cfResult.get()
    }
}
