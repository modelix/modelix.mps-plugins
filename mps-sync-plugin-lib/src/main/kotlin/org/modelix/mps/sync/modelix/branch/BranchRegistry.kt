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
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.mps.RepositoryChangeListener
import org.modelix.mps.sync.mps.services.InjectableService
import org.modelix.mps.sync.mps.services.ServiceLocator

/**
 * A registry to store the modelix [IBranch] we are connected to and the [ReplicatedModel] built from the content of it.
 * This can be used to access the most recent content of the branch as a tree of [INode]s.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class BranchRegistry : InjectableService {

    /**
     * A collector class to simplify injecting the commonly used services in the sync plugin.
     */
    private lateinit var serviceLocator: ServiceLocator

    /**
     * The active [SRepository] to access the [SModel]s and [SModule]s in MPS.
     */
    private val mpsRepository: SRepository
        get() = serviceLocator.mpsRepository

    /**
     * The content of the branch we are connected to.
     */
    var model: ReplicatedModel? = null
        private set

    /**
     * The modelix client we use to connect to the branch.
     */
    private var client: ModelClientV2? = null

    /**
     * The identifier of the branch we are connected to.
     */
    private var branchReference: BranchReference? = null

    /**
     * The change listener of the branch we are connected, so we get notified if something on the model server changed.
     */
    private var branchListener: ModelixBranchListener? = null

    /**
     * The change listener that is called if a change in the active [SRepository] occurred.
     */
    private var repoChangeListener: RepositoryChangeListener? = null

    override fun initService(serviceLocator: ServiceLocator) {
        this.serviceLocator = serviceLocator
    }

    /**
     * @return the modelix branch we are connected to.
     */
    fun getBranch() = model?.getBranch()

    /**
     * If the parameter branch is the branch we are connected to, then we dispose that branch.
     *
     * @param branch the branch to disconnect from.
     */
    fun unsetBranch(branch: IBranch) {
        if (branch == getBranch()) {
            dispose()
        }
    }

    /**
     * Connect to the branch identified by [branchReference] and return the branch's content as a [ReplicatedModel].
     *
     * The branch's state depends on [ReplicatedModelInitContext.initialVersion] which tells the version of the branch
     * to use as a base. I.e. we assume that the modules and models running MPS are at
     * [ReplicatedModelInitContext.initialVersion], and we would like to get the changes between that version and the
     * most recent version. The [branchListener] will be registered to the branch so we can play these changes into MPS.
     *
     * @param client the modelix client to use for connecting to the branch.
     * @param branchReference the identifier of the branch.
     * @param languageRepository the [MPSLanguageRepository] to be used in the [branchListener] (that will be registered
     * to the branch as a change listener).
     * @param replicatedModelContext some contextual information about the [ReplicatedModel]. I.e. the coroutine scope
     * to use ([ReplicatedModelInitContext.coroutineScope]), and the initial version to start the branch with
     * ([ReplicatedModelInitContext.initialVersion]).
     *
     * @return the branch's content as a [ReplicatedModel]
     */
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

    /**
     * Disconnects the active branch, removes its event listener. Besides,the [repoChangeListener] is also unregistered.
     */
    override fun dispose() {
        try {
            val branch = getBranch()
            if (branch != null && branchListener != null) {
                branch.removeListener(branchListener!!)
            }
        } catch (ignored: Exception) {
            // getBranch() may throw IllegalStateException exception if ReplicatedModel has not started yet
        }

        try {
            model?.dispose()
        } catch (ignored: Exception) {
            // so that the other parts of the dispose() call are not affected
        }

        repoChangeListener?.let { mpsRepository.removeRepositoryListener(it) }

        model = null
        branchReference = null
        client = null
    }

    /**
     * Registers a [ModelixBranchListener] to the [branch] with the [languageRepository].
     *
     * @param branch the branch to which we register the [ModelixBranchListener] change listener.
     * @param languageRepository the [MPSLanguageRepository] to be used in the branch change listener.
     */
    private fun registerBranchListener(branch: IBranch, languageRepository: MPSLanguageRepository) {
        branchListener = ModelixBranchListener(branch, serviceLocator, languageRepository)
        branch.addListener(branchListener!!)
    }
}

/**
 * Some contextual information about the [ReplicatedModel].
 *
 * @property coroutineScope the [CoroutineScope] to run the [ReplicatedModel] on.
 * @property initialVersion the initial version to start the branch with. I.e. we assume that the modules and models
 * running MPS are at this version.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class ReplicatedModelInitContext(
    val coroutineScope: CoroutineScope,
    val initialVersion: CLVersion? = null,
)
