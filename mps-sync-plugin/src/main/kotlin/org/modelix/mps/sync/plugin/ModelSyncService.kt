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

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import mu.KLogger
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.IRebindModulesSyncService
import org.modelix.mps.sync.ISyncService
import org.modelix.mps.sync.mps.notifications.BalloonNotifier
import org.modelix.mps.sync.mps.notifications.WrappedNotifier
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.mps.util.ModuleIdWithName
import org.modelix.mps.sync.plugin.action.ModelixActionGroup
import java.net.URL

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Service(Service.Level.PROJECT)
class ModelSyncService(project: Project) : IRebindModulesSyncService {

    private val logger: KLogger = KotlinLogging.logger { }

    private val notifier: WrappedNotifier
    private val syncService: ISyncService

    init {
        val serviceLocator = project.service<ServiceLocator>()
        notifier = serviceLocator.wrappedNotifier
        syncService = serviceLocator.syncService

        logger.debug { "ModelixSyncPlugin: Registering sync actions" }
        registerSyncActions()
        logger.debug { "ModelixSyncPlugin: Registration finished" }

        logger.debug { "ModelixSyncPlugin: Initializing the InjectableNotifierWrapper" }
        notifier.setNotifier(BalloonNotifier(project))
        logger.debug { "ModelixSyncPlugin: InjectableNotifierWrapper is initialized" }
    }

    override fun connectModelServer(serverURL: String, jwt: String?): ModelClientV2? {
        var client: ModelClientV2? = null
        try {
            client = syncService.connectModelServer(URL(serverURL), jwt)
            notifier.notifyAndLogInfo("Connected to server: $serverURL", logger)
        } catch (t: Throwable) {
            val message = "Unable to connect to $serverURL. Cause: ${t.message}"
            notifier.notifyAndLogError(message, t, logger)
        }
        return client
    }

    override fun rebindModules(
        client: ModelClientV2,
        branchReference: BranchReference,
        initialVersion: CLVersion,
        modules: Iterable<AbstractModule>,
    ): Iterable<IBinding>? {
        try {
            return syncService.rebindModules(client, branchReference, initialVersion, modules)
        } catch (t: Throwable) {
            val message = "Error while binding modules to Branch '$branchReference'. Cause: ${t.message}"
            notifier.notifyAndLogError(message, t, logger)
            return null
        }
    }

    fun disconnectServer(modelClient: ModelClientV2): ModelClientV2? {
        var client: ModelClientV2? = modelClient
        val baseUrl = modelClient.baseUrl
        try {
            syncService.disconnectModelServer(modelClient)
            notifier.notifyAndLogInfo("Disconnected from server: $baseUrl", logger)
            client = null
        } catch (t: Throwable) {
            val message = "Unable to disconnect from $baseUrl. Cause: ${t.message}"
            notifier.notifyAndLogError(message, t, logger)
        }
        return client
    }

    fun connectToBranch(client: ModelClientV2, branchReference: BranchReference): IBranch? {
        try {
            val branch = syncService.connectToBranch(client, branchReference)
            notifier.notifyAndLogInfo("Connected to branch: $branchReference", logger)
            return branch
        } catch (t: Throwable) {
            val message = "Unable to connect to branch ${branchReference.branchName}. Cause: ${t.message}"
            notifier.notifyAndLogError(message, t, logger)
            return null
        }
    }

    fun disconnectFromBranch(branch: IBranch, branchName: String) {
        try {
            syncService.disconnectFromBranch(branch, branchName)
            notifier.notifyAndLogInfo("Disconnected from branch: $branchName", logger)
        } catch (t: Throwable) {
            val message = "Unable to disconnect from branch $branchName. Cause: ${t.message}"
            notifier.notifyAndLogError(message, t, logger)
        }
    }

    fun getActiveBranch() = syncService.getActiveBranch()

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
                module,
            ).forEach { it.activate() }
        } catch (t: Throwable) {
            val message =
                "Error while binding Module '${module.name}' from Repository '$repositoryID' and Branch '$branchName'. Cause: ${t.message}"
            notifier.notifyAndLogError(message, t, logger)
        }
    }

    fun bindModuleFromMps(module: AbstractModule, branch: IBranch) = syncService.bindModuleFromMps(module, branch)

    fun bindModelFromMps(model: SModelBase, branch: IBranch) = syncService.bindModelFromMps(model, branch)

    private fun registerSyncActions() {
        listOf(
            "jetbrains.mps.ide.actions.ModelActions_ActionGroup",
            "jetbrains.mps.ide.actions.SolutionActions_ActionGroup",
        ).forEach {
            val actionGroup = ActionManager.getInstance().getAction(it)
            if (actionGroup is DefaultActionGroup) {
                val modelixActionGroup = ModelixActionGroup()
                val actionsAreRegistered = actionGroup.childActionsOrStubs.contains(modelixActionGroup)
                if (!actionsAreRegistered) {
                    actionGroup.run {
                        addSeparator()
                        add(ModelixActionGroup())
                    }
                }
            } else {
                logger.error { "Action Group $it was not found, thus the UI actions are not registered." }
            }
        }
    }
}
