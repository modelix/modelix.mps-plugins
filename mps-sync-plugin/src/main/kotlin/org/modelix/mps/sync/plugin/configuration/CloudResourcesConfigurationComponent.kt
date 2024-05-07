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
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap

// TODO move it into the mps-sync-plugin-lib project, because we want to use it in headless mode (without plugin UI) too
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
@Service(Service.Level.PROJECT)
@State(
    name = "CloudResources",
    reloadable = true,
    storages = [Storage("cloudSettings.xml", roamingType = RoamingType.DISABLED)],
)
class CloudResourcesConfigurationComponent : PersistentStateComponent<CloudResourcesConfigurationComponent.State> {

    // private val dispatcher = Dispatchers.IO // rather IO-intensive tasks

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
     * WARNING about the State's fields:
     * - Mutable collections will not be persisted!!!
     * - Maps and Collections will only be persisted 2 layers deep (List<List<String>> works, but
     * List<List<List<String>>> not)
     * - Pairs will not be persisted
     */
    inner class State {

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
            ActiveMpsProjectInjector.runMpsReadAction {
                synchronizationCache = MpsToModelixMap.Serializer().serialize()
            }

            /*
            // TODO fixme
            val replicatedModel = BranchRegistry.model
            // TODO is there a better way than a dirty cast?
            clientUrl = (replicatedModel?.client as ModelClientV2).baseUrl
            repositoryId = replicatedModel.branchRef.repositoryId.id
            branchName = replicatedModel.branchRef.branchName

            runBlocking(dispatcher) {
                localVersion = replicatedModel.getCurrentVersion().getContentHash()
            }

            BindingsRegistry.getModuleBindings().forEach {
                moduleIds = moduleIds + it.module.moduleId.toString()
            }*/

            return this
        }

        fun load() {
            if (synchronizationCache.isNotEmpty()) {
                ActiveMpsProjectInjector.runMpsReadAction {
                    MpsToModelixMap.Serializer().deserialize(synchronizationCache)
                }
            }

            /*val sRepository = ActiveMpsProjectInjector.activeMpsProject!!.repository
            val modulesFuture = CompletableFuture<List<SModule>>()
            ActiveMpsProjectInjector.activeMpsProject!!.modelAccess.runReadAction {
                val modules = mutableListOf<SModule>()
                for (moduleId in moduleIds) {
                    val id = PersistenceFacade.getInstance().createModuleId(moduleId)
                    val module = sRepository.getModule(id as ModuleId)
                    assert(module != null) { "Could not restore module from id. [id = $id]" }
                    modules.add(module!!)
                }
                modulesFuture.complete(modules)
            }
            val modules = modulesFuture.get()

            val syncService = service<ModelSyncService>()
            modules.forEach {
                // TODO fixme authentication
                // val jwt = null
                // syncService.rebindModule(clientUrl, jwt, branchName, it, repositoryId, localVersion)
            }*/
        }
    }
}
