/*
 * Copyright (c) 2023-2024.
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

package org.modelix.mps.sync.plugin.configuration

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import jetbrains.mps.project.AbstractModule
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.modelix.BranchRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.notifications.InjectableNotifierWrapper
import org.modelix.mps.sync.plugin.ModelSyncService
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import java.util.concurrent.CompletableFuture

// TODO move it into the mps-sync-plugin-lib project, because we want to use it in headless mode (without plugin UI) too
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
@Service(Service.Level.PROJECT)
@State(
    name = "CloudResources",
    reloadable = true,
    storages = [Storage("cloudSettings.xml", roamingType = RoamingType.DISABLED)],
)
class CloudResourcesConfigurationComponent : PersistentStateComponent<CloudResourcesConfigurationComponent.State> {

    override fun getState(): State {
        return State().getCurrentState()
    }

    override fun loadState(newState: State) {
        newState.load()
    }

    /**
     * States are capable of taking a snapshot of the current bindings to modelix servers with getCurrentState(),
     * and recreating that state by calling load(). Note that currently the bindings of the state will be added during
     * load(), but not included bindings will be disconnected.
     *
     * States will be automatically saved when a project is closed, and automatically loaded, when that project is
     * reopened. Technically, it should also be possible to create a state yourself in order to load it, although this
     * is not what they were made for.
     *
     * WARNING:
     * About the State class:
     * - do not make it an inner class, otherwise deserialization will fail with an exception
     * About the State's fields:
     * - Mutable collections will not be persisted!!!
     * - Maps and Collections will only be persisted 2 layers deep (List<List<String>> works, but List<List<List<String>>> not)
     * - Pairs will not be persisted
     */
    class State {

        private val logger = KotlinLogging.logger {}
        private val notifier = InjectableNotifierWrapper

        // modelix connection
        var clientUrl: String = ""
        var repositoryId: String = ""
        var branchName: String = ""
        var localVersion: String = ""

        // synchronized modules
        var moduleIds: List<String> = listOf()

        // MPS to Modelix mapping
        var synchronizationCache: String = ""

        fun getCurrentState(): State {
            val replicatedModel = BranchRegistry.model
            if (replicatedModel == null) {
                notifier.notifyAndLogWarning(
                    "Replicated Model is null, therefore an empty State will be saved.",
                    logger,
                )
                return this
            }

            clientUrl = (replicatedModel.client as ModelClientV2).baseUrl
            repositoryId = replicatedModel.branchRef.repositoryId.id
            branchName = replicatedModel.branchRef.branchName

            runBlocking {
                localVersion = replicatedModel.getCurrentVersion().getContentHash()
            }

            BindingsRegistry.getModuleBindings().forEach {
                moduleIds += it.module.moduleId.toString()
            }

            ActiveMpsProjectInjector.runMpsReadAction {
                synchronizationCache = MpsToModelixMap.Serializer().serialize()
            }

            return this
        }

        fun load() {
            try {
                if (synchronizationCache.isNotEmpty()) {
                    ActiveMpsProjectInjector.runMpsReadAction {
                        // TODO testme what happens if deserialization fails because of an exception
                        MpsToModelixMap.Serializer().deserialize(synchronizationCache)
                        logger.debug { "Synchronization cache is restored." }
                    }
                } else {
                    notifier.notifyAndLogWarning(
                        "Serialized synchronization cache is empty, thus it is not restored.",
                        logger,
                    )
                }

                logger.debug { "Restoring connection to model server." }
                // TODO fixme this service<ModelSyncService>() not work in SECURE
                val syncService = service<ModelSyncService>()
                val client = syncService.connectModelServer(clientUrl, "")
                if (client == null) {
                    val exception =
                        IllegalStateException("Connection to $clientUrl failed, thus cloud synchronization plugin state is not restored.")
                    notifier.notifyAndLogError(exception.message!!, exception, logger)
                    return
                }
                syncService.registerUnattendedClient(client)

                val repositoryId = RepositoryId(repositoryId)
                var initialVersion: CLVersion
                runBlocking {
                    // TODO testme exception
                    try {
                        initialVersion = client.loadVersion(repositoryId, localVersion, null) as CLVersion
                    } catch (t: Throwable) {
                        client.close()
                        throw t
                    }
                }
                logger.debug { "Connection to model server is restored." }

                logger.debug { "Restoring SModules." }
                val modulesFuture = CompletableFuture<List<AbstractModule>>()
                ActiveMpsProjectInjector.runMpsReadAction { repository ->
                    // TODO test what happens if an Exception has occurred
                    val modules = moduleIds.map {
                        val id = PersistenceFacade.getInstance().createModuleId(it)
                        val module = repository.getModule(id)
                        requireNotNull(module) { "Could not restore module from ID ($id)." }
                        require(module is AbstractModule) { "Module ($module) is not an AbstractModule." }
                        module
                    }
                    modulesFuture.complete(modules)
                    logger.debug { "${modules.count()} SModules are restored." }
                }

                logger.debug { "Recreating Bindings." }
                val modules = modulesFuture.get()
                val branchReference = BranchReference(repositoryId, branchName)
                syncService.rebindModules(client, branchReference, initialVersion, modules)
                val bindings: Iterable<IBinding> = listOf()

                logger.debug { "Bindings are recreated, now activating them." }
                bindings.forEach(IBinding::activate)
                logger.debug { "Bindings are activated." }

                // TODO test this whole scenario on the happy path
                // TODO test it on the not-happy path (i.e. exceptions occur in the two ActiveMpsProjectInjector.runMpsReadAction actions)
            } catch (t: Throwable) {
                val message =
                    "Error occurred, while restoring persisted state. Connection to model server, bindings and synchronization cache might not be established and activated. Please check logs for details."
                notifier.notifyAndLogError(message, t, logger)
            }
        }
    }
}
