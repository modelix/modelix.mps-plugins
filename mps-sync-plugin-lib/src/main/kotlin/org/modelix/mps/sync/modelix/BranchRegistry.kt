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
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.RepositoryChangeListener
import java.util.Objects

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
object BranchRegistry : AutoCloseable {

    var branch: IBranch? = null
        private set

    private var branchReference: BranchReference? = null

    var model: ReplicatedModel? = null
        private set
    private lateinit var branchListener: ModelixBranchListener

    // the MPS Project and its registered change listener
    private lateinit var project: MPSProject
    private lateinit var repoChangeListener: RepositoryChangeListener

    fun unsetBranch(branch: IBranch) {
        if (Objects.equals(this.branch, branch)) {
            close()
        }
    }

    suspend fun setBranch(
        client: ModelClientV2,
        branchReference: BranchReference,
        languageRepository: MPSLanguageRepository,
        targetProject: MPSProject,
        replicatedModelContext: ReplicatedModelInitContext,
    ): IBranch {
        if (this.branchReference == branchReference) {
            return branch!!
        }

        close()

        if (replicatedModelContext.initialVersion != null) {
            model = ReplicatedModel(
                client,
                branchReference,
                replicatedModelContext.coroutineScope,
                replicatedModelContext.initialVersion,
            )
            /*
             * Register branch listener before starting, because initialVersion contains the data already and we want
             * to react to the changes that come after initialVersion.
             */
            branch = model!!.getBranch()
            registerBranchListener(branch!!, languageRepository)
            model!!.start()
        } else {
            model = client.getReplicatedModel(branchReference, replicatedModelContext.coroutineScope)
            // Register branch listener after stating, because we will start with the latest version anyway.
            branch = model!!.start()
            registerBranchListener(branch!!, languageRepository)
        }

        val repositoryChangeListener = RepositoryChangeListener(branch!!)
        targetProject.repository.addRepositoryListener(repositoryChangeListener)
        project = targetProject
        repoChangeListener = repositoryChangeListener

        return branch!!
    }

    override fun close() {
        if (branch == null) {
            return
        }

        // remove listeners
        branch!!.removeListener(branchListener)
        project.repository.removeRepositoryListener(repoChangeListener)

        model?.dispose()

        branch = null
    }

    private fun registerBranchListener(branch: IBranch, languageRepository: MPSLanguageRepository) {
        branchListener = ModelixBranchListener(branch, languageRepository)
        branch.addListener(branchListener)
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class ReplicatedModelInitContext(
    val coroutineScope: CoroutineScope,
    val initialVersion: CLVersion? = null,
)
