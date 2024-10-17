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
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.IRebindModulesSyncService
import org.modelix.mps.sync.ISyncService
import org.modelix.mps.sync.SyncServiceImpl
import org.modelix.mps.sync.mps.notifications.BalloonNotifier
import org.modelix.mps.sync.mps.notifications.WrappedNotifier
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.mps.util.ModuleIdWithName
import org.modelix.mps.sync.plugin.action.ModelixActionGroup
import java.net.URL

/**
 * The bridge between the modelix sync plugin's UI and the [SyncServiceImpl] that is actually coordinating the
 * synchronization process. Most of its methods are doing the same thing as in [SyncServiceImpl] plus with some
 * additional user notifications about the operation results via the [notifier] field.
 *
 * @see [IRebindModulesSyncService].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Service(Service.Level.PROJECT)
class ModelSyncService(project: Project) : IRebindModulesSyncService {

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * A notifier that can notify the user about certain messages in a nicer way than just simply logging the message.
     */
    private val notifier: WrappedNotifier

    /**
     * The entry class of the synchronization workflows.
     */
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

    /**
     * Disconnects the [modelClient] from the server.
     *
     * @param modelClient the model client to disconnect from the server.
     *
     * @return null if the disconnection was successful, otherwise the living client.
     *
     * @see [SyncServiceImpl.disconnectModelServer].
     */
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

    /**
     * Connects the [client] to the branch identified by its [branchReference].
     *
     * @param client the client to use for the connection.
     * @param branchReference the identifier of the branch to connect to.
     *
     * @return the active branch we are connected to or null if an exception occurred.
     *
     * @see [SyncServiceImpl.connectToBranch].
     */
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

    /**
     * Disconnects from the [branch].
     *
     * @param branch the active branch to disconnect.
     * @param branchName the name of the active branch.
     *
     * @see [SyncServiceImpl.disconnectFromBranch].
     */
    fun disconnectFromBranch(branch: IBranch, branchName: String) {
        try {
            syncService.disconnectFromBranch(branch, branchName)
            notifier.notifyAndLogInfo("Disconnected from branch: $branchName", logger)
        } catch (t: Throwable) {
            val message = "Unable to disconnect from branch $branchName. Cause: ${t.message}"
            notifier.notifyAndLogError(message, t, logger)
        }
    }

    /**
     * @return the active branch.
     *
     * @see [SyncServiceImpl.getActiveBranch].
     */
    fun getActiveBranch() = syncService.getActiveBranch()

    /**
     * Binds the [module] from the [branchName] branch of the repository, identified by its [repositoryID], on the model
     * server to which we are connected by the [client].
     *
     * After binding the [module], the method activates the created [IBinding]s automatically.
     *
     * @param client the model client we use to connect to the model server.
     * @param branchName the name of the branch from which we want to bind the Module.
     * @param module the ID and name of the Module we want to bind.
     * @param repositoryID the ID of the repository on the model server.
     */
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

    /**
     * @see [SyncServiceImpl.bindModuleFromMps].
     */
    fun bindModuleFromMps(module: AbstractModule, branch: IBranch) = syncService.bindModuleFromMps(module, branch)

    /**
     * @see [SyncServiceImpl.bindModelFromMps].
     */
    fun bindModelFromMps(model: SModelBase, branch: IBranch) = syncService.bindModelFromMps(model, branch)

    /**
     * Registers the modelix sync plugin's UI actions to MPS, so that they can be triggered from the context menu of
     * the [SModule]s and [SModel]s in the Project explorer window.
     *
     * @see [ModelixActionGroup].
     */
    private fun registerSyncActions() {
        listOf(
            "jetbrains.mps.ide.actions.ModelActions_ActionGroup",
            "jetbrains.mps.ide.actions.SolutionActions_ActionGroup",
        ).forEach {
            val actionGroup = ActionManager.getInstance().getAction(it)
            if (actionGroup is DefaultActionGroup) {
                val modelixActionGroup = ModelixActionGroup
                val actionsAreRegistered = actionGroup.childActionsOrStubs.contains(modelixActionGroup)
                if (!actionsAreRegistered) {
                    actionGroup.run {
                        addSeparator()
                        add(modelixActionGroup)
                    }
                }
            } else {
                logger.error { "Action Group $it was not found, thus the UI actions are not registered." }
            }
        }
    }
}
