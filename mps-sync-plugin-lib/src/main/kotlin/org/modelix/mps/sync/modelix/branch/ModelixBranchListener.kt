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

package org.modelix.mps.sync.modelix.branch

import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ITree
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.transformation.modelixToMps.incremental.ModelixTreeChangeVisitor

/**
 * The change listener that is called by modelix if the content of the branch changes on the model server.
 *
 * @param branch the branch whose content we are observing.
 * @param serviceLocator a collector class to simplify injecting the commonly used services in the sync plugin.
 * @param languageRepository the [MPSLanguageRepository] to be used to resolve Concepts.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelixBranchListener(
    branch: IBranch,
    serviceLocator: ServiceLocator,
    languageRepository: MPSLanguageRepository,
) : IBranchListener {

    /**
     * The visitor that is called if there is a difference between the remote version of the branch (that is on the
     * model server) and the local version (that is running in MPS).
     */
    private val visitor = ModelixTreeChangeVisitor(branch, serviceLocator, languageRepository)

    override fun treeChanged(oldTree: ITree?, newTree: ITree) {
        oldTree?.let { newTree.visitChanges(it, visitor) }
    }
}
