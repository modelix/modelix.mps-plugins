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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.SyncServiceImpl
import org.modelix.mps.sync.plugin.action.ModelixActionGroup
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
@Service(Service.Level.APP)
class ModelSyncService : Disposable {

    private val logger = KotlinLogging.logger {}
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var server: String? = null

    private val syncService = SyncServiceImpl()

    init {
        logger.info { "============================================ Registering sync actions" }
        registerSyncActions()

        logger.info { "============================================ Registration finished" }

        logger.info { "============================================ Sync Service initialized $syncService" }
    }

    fun connectModelServer(
        url: String,
        jwt: String,
        callback: (() -> Unit)? = null,
    ): ModelClientV2? {
        return runBlocking(coroutineScope.coroutineContext) {
            var client: ModelClientV2? = null
            try {
                logger.info { "Connection to server: $url" }
                client = syncService.connectModelServer(URL(url), jwt, callback)
                logger.info { "Connected to server: $url" }
            } catch (ex: Exception) {
                logger.error(ex) { "Unable to connect" }
            }
            return@runBlocking client
        }
    }

    fun disconnectServer(
        modelClient: ModelClientV2,
        callback: (() -> Unit)? = null,
    ): ModelClientV2? {
        var client: ModelClientV2? = modelClient
        return runBlocking(coroutineScope.coroutineContext) {
            try {
                logger.info { "Disconnecting from server: ${modelClient.baseUrl}" }
                syncService.disconnectModelServer(modelClient, callback)
                logger.info { "Disconnected from server: ${modelClient.baseUrl}" }
                client = null
            } catch (ex: Exception) {
                logger.error(ex) { "Unable to disconnect" }
            }
            return@runBlocking client
        }
    }

    fun bindModule(
        client: ModelClientV2,
        branchName: String,
        moduleId: String,
        repositoryID: String,
    ) {
        coroutineScope.launch {
            try {
                syncService.bindModule(
                    client,
                    BranchReference(RepositoryId(repositoryID), branchName),
                    moduleId,
                ).forEach { it.activate() }
            } catch (ex: Exception) {
                logger.error(ex) { "Error while binding module" }
            }
        }
    }

    fun ensureStarted() {
        logger.info { "============================================  ensureStarted" }
    }

    override fun dispose() {
        logger.info { "============================================  dispose" }
        syncService.dispose()
        ensureStopped()
    }

    @Synchronized
    private fun ensureStopped() {
        logger.info { "============================================  ensureStopped" }
        if (server == null) return
        logger.info { "stopping modelix server" }
        server = null
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
                logger.error { "Action Group $it was not found, thus the UI actions are not registered there." }
            }
        }
    }
}
