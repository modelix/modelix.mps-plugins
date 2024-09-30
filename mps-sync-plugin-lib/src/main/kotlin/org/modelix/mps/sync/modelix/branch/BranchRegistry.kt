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

package org.modelix.mps.sync.modelix.branch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.RepositoryChangeListener
import org.modelix.mps.sync.mps.services.InjectableService
import org.modelix.mps.sync.mps.services.ServiceLocator

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class BranchRegistry : InjectableService {

    private lateinit var serviceLocator: ServiceLocator

    private val mpsRepository: SRepository
        get() = serviceLocator.mpsRepository

    var model: ReplicatedModel? = null
        private set

    private var client: ModelClientV2? = null
    private var branchReference: BranchReference? = null

    private lateinit var branchListener: ModelixBranchListener
    private lateinit var repoChangeListener: RepositoryChangeListener

    override fun initService(serviceLocator: ServiceLocator) {
        this.serviceLocator = serviceLocator
    }

    fun getBranch() = model?.getBranch()

    fun unsetBranch(branch: IBranch) {
        if (branch == getBranch()) {
            dispose()
        }
    }

    fun setReplicatedModel(
        client: ModelClientV2,
        branchReference: BranchReference,
        languageRepository: MPSLanguageRepository,
        replicatedModelContext: ReplicatedModelInitContext,
    ): ReplicatedModel {
        if (this.client == client && this.branchReference == branchReference) {
            return model!!
        }

        dispose()

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

        val repositoryChangeListener = RepositoryChangeListener(branch, serviceLocator)
        mpsRepository.addRepositoryListener(repositoryChangeListener)
        repoChangeListener = repositoryChangeListener

        return model!!
    }

    override fun dispose() {
        val branch = getBranch() ?: return
        branch.removeListener(branchListener)
        mpsRepository.removeRepositoryListener(repoChangeListener)

        model?.dispose()

        model = null
        branchReference = null
        client = null
    }

    private fun registerBranchListener(branch: IBranch, languageRepository: MPSLanguageRepository) {
        branchListener = ModelixBranchListener(branch, serviceLocator, languageRepository)
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
