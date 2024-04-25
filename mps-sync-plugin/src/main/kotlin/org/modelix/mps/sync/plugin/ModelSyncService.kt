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

package org.modelix.mps.sync.plugin
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

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.ISyncService
import org.modelix.mps.sync.SyncServiceImpl
import org.modelix.mps.sync.mps.notifications.BalloonNotifier
import org.modelix.mps.sync.mps.notifications.InjectableNotifierWrapper
import org.modelix.mps.sync.plugin.action.ModelixActionGroup
import org.modelix.mps.sync.plugin.gui.ModuleIdWithName
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
@Service(Service.Level.APP)
class ModelSyncService : Disposable {

    private val logger = KotlinLogging.logger { }
    private val notifierInjector = InjectableNotifierWrapper

    private lateinit var syncService: ISyncService

    init {
        logger.debug { "ModelixSyncPlugin: Registering sync actions" }
        registerSyncActions()
        logger.debug { "ModelixSyncPlugin: Registration finished" }
    }

    fun connectModelServer(url: String, jwt: String): ModelClientV2? {
        var client: ModelClientV2? = null
        try {
            logger.info { "Connection to server: $url" }
            client = syncService.connectModelServer(URL(url), jwt)
            logger.info { "Connected to server: $url" }
        } catch (t: Throwable) {
            val message = "Unable to connect to $url. Cause: ${t.message}"
            notifierInjector.notifyAndLogError(message, t, logger)
        }
        return client
    }

    fun disconnectServer(modelClient: ModelClientV2): ModelClientV2? {
        var client: ModelClientV2? = modelClient
        val baseUrl = modelClient.baseUrl
        try {
            logger.info { "Disconnecting from  server: $baseUrl" }
            syncService.disconnectModelServer(modelClient)
            logger.info { "Disconnected from server: $baseUrl" }
            client = null
        } catch (t: Throwable) {
            val message = "Unable to disconnect from $baseUrl. Cause: ${t.message}"
            notifierInjector.notifyAndLogError(message, t, logger)
        }
        return client
    }

    fun connectToBranch(client: ModelClientV2, branchReference: BranchReference) {
        try {
            logger.info { "Connecting to branch $branchReference" }
            syncService.connectToBranch(client, branchReference)
            logger.info { "Connection to branch $branchReference is established" }
        } catch (t: Throwable) {
            val message = "Unable to connect to branch ${branchReference.branchName}. Cause: ${t.message}"
            notifierInjector.notifyAndLogError(message, t, logger)
        }
    }

    fun bindModuleFromServer(
        client: ModelClientV2,
        branchName: String,
        module: ModuleIdWithName,
        repositoryID: String,
    ) {
        try {
            syncService.bindModuleFromServer(
                client,
                BranchReference(RepositoryId(repositoryID), branchName),
                module.id,
            ).forEach { it.activate() }
        } catch (t: Throwable) {
            val message =
                "Error while binding Module '${module.name}' from Repository '$repositoryID' and Branch '$branchName'. Cause: ${t.message}"
            notifierInjector.notifyAndLogError(message, t, logger)
        }
    }

    fun bindModuleFromMps(module: AbstractModule, branch: IBranch) = syncService.bindModuleFromMps(module, branch)

    fun bindModelFromMps(model: SModelBase, branch: IBranch) = syncService.bindModelFromMps(model, branch)

    fun setActiveProject(project: Project) {
        // TODO FIXME ModelSyncService MUST BE Project scoped instead of APP scoped, otherwise this workaround here is fragile
        logger.debug { "ModelixSyncPlugin: Initializing Sync Service" }
        val notifier = BalloonNotifier(project)
        syncService = SyncServiceImpl(notifier)
        logger.debug { "ModelixSyncPlugin: Sync Service is initialized" }

        syncService.setActiveProject(project)
    }

    fun ensureStarted() {
        logger.debug { "ModelixSyncPlugin: EnsureStarted" }
    }

    override fun dispose() {
        logger.debug { "ModelixSyncPlugin: Dispose" }
        syncService.close()
    }

    private fun registerSyncActions() {
        listOf(
            "jetbrains.mps.ide.actions.ModelActions_ActionGroup",
            "jetbrains.mps.ide.actions.SolutionActions_ActionGroup",
        ).forEach {
            val actionGroup = ActionManager.getInstance().getAction(it)
            if (actionGroup is DefaultActionGroup) {
                actionGroup.run {
                    addSeparator()
                    add(ModelixActionGroup())
                }
            } else {
                logger.error { "Action Group $it was not found, thus the UI actions are not registered." }
            }
        }
    }
}
