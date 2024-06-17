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

package org.modelix.mps.sync.modelix

import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.RepositoryChangeListener

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
object BranchRegistry : AutoCloseable {

    val branch: IBranch?
        get() = model?.getBranch()

    var model: ReplicatedModel? = null
        private set

    // TODO refactor it so we can get as many replicated models from as many clients and branches as we wish...
    private var client: ModelClientV2? = null
    private var branchReference: BranchReference? = null

    private lateinit var branchListener: ModelixBranchListener

    // the MPS Project and its registered change listener
    private lateinit var project: MPSProject
    private lateinit var repoChangeListener: RepositoryChangeListener

    fun unsetBranch(branch: IBranch) {
        if (branch == this.branch) {
            close()
        }
    }

    fun setReplicatedModel(
        client: ModelClientV2,
        branchReference: BranchReference,
        languageRepository: MPSLanguageRepository,
        targetProject: MPSProject,
        replicatedModelContext: ReplicatedModelInitContext,
    ): ReplicatedModel {
        if (this.client == client && this.branchReference == branchReference) {
            return model!!
        }

        close()

        val coroutineScope = replicatedModelContext.coroutineScope
        val initialVersion = replicatedModelContext.initialVersion
        model = ReplicatedModel(client, branchReference, coroutineScope, initialVersion)
        if (initialVersion == null) {
            // we must start the replicated model, otherwise getBranch will throw an exception
            runBlocking(coroutineScope.coroutineContext) {
                model!!.start()
            }
        }

        val branch = model!!.getBranch()
        registerBranchListener(branch, languageRepository)

        this.branchReference = branchReference
        this.client = client

        val repositoryChangeListener = RepositoryChangeListener(branch)
        targetProject.repository.addRepositoryListener(repositoryChangeListener)
        project = targetProject
        repoChangeListener = repositoryChangeListener

        return model!!
    }

    override fun close() {
        if (branch == null) {
            return
        }

        // remove listeners
        branch!!.removeListener(branchListener)
        project.repository.removeRepositoryListener(repoChangeListener)

        model?.dispose()

        model = null
        branchReference = null
        client = null
    }

    private fun registerBranchListener(branch: IBranch, languageRepository: MPSLanguageRepository) {
        branchListener = ModelixBranchListener(branch, languageRepository)
        branch.addListener(branchListener)
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class ReplicatedModelInitContext(
    val coroutineScope: CoroutineScope,
    val initialVersion: CLVersion? = null,
)
