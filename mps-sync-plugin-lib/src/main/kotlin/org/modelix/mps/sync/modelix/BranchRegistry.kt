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

import com.intellij.openapi.Disposable
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.RepositoryChangeListener

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object BranchRegistry : Disposable {

    var branch: IBranch? = null
        private set

    private var branchReference: BranchReference? = null

    private lateinit var model: ReplicatedModel
    private lateinit var branchListener: ModelixBranchListener

    // the MPS Project and its registered change listener
    private lateinit var project: MPSProject
    private lateinit var repoChangeListener: RepositoryChangeListener

    suspend fun setBranch(
        client: ModelClientV2,
        branchReference: BranchReference,
        languageRepository: MPSLanguageRepository,
        targetProject: MPSProject,
        replicatedModelCoroutineScope: CoroutineScope,
    ): IBranch {
        if (this.branchReference == branchReference) {
            return branch!!
        }

        dispose()

        model = client.getReplicatedModel(branchReference, replicatedModelCoroutineScope)
        branch = model.start()

        branchListener = ModelixBranchListener(model, languageRepository, branch!!)
        branch!!.addListener(branchListener)

        val repositoryChangeListener = RepositoryChangeListener(branch!!)
        targetProject.repository.addRepositoryListener(repositoryChangeListener)
        project = targetProject
        repoChangeListener = repositoryChangeListener

        return branch!!
    }

    override fun dispose() {
        if (branch == null) {
            return
        }

        // remove listeners
        branch!!.removeListener(branchListener)
        project.repository.removeRepositoryListener(repoChangeListener)

        model.dispose()

        branch = null
    }
}
