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
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.INode
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
    val activeClients = mutableSetOf<ModelClientV2>()

    init {
        logger.info { "============================================ Registering sync actions" }
        registerSyncActions()

        logger.info { "============================================ Registration finished" }

        logger.info { "============================================ Sync Service initialized $syncService" }
    }

    fun connectModelServer(
        url: String,
        jwt: String,
        callback: (() -> Unit),
    ) {
        coroutineScope.launch {
            try {
                logger.info { "Connection to server: $url" }
                val client = syncService.connectModelServer(URL(url), jwt)
                activeClients.add(client)
                logger.info { "Connected to server: $url" }
                callback()
            } catch (ex: Exception) {
                logger.error(ex) { "Unable to connect" }
            }
        }
    }

    fun disconnectServer(
        modelClient: ModelClientV2,
        callback: (() -> Unit),
    ) {
        coroutineScope.launch {
            try {
                logger.info { "disconnecting to server: ${modelClient.baseUrl}" }
                syncService.disconnectModelServer(modelClient)
                activeClients.remove(modelClient)
                callback()
                logger.info { "disconnected server: ${modelClient.baseUrl}" }
            } catch (ex: Exception) {
                logger.error(ex) { "Unable to disconnect" }
            }
        }
    }

    fun bindModule(
        client: ModelClientV2,
        branchName: String,
        module: INode,
        repositoryID: String,
    ) {
        coroutineScope.launch {
            try {
                syncService.bindModule(
                    client,
                    BranchReference(RepositoryId(repositoryID), branchName),
                    module,
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
