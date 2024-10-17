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
import com.intellij.openapi.project.Project
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.persistence.PersistableState
import org.modelix.mps.sync.persistence.PluginStatePersister

/**
 * It is responsible for automatically loading and persisting the sync plugin's state via the [PersistableState] class,
 * using intellij's [PersistentStateComponent] interface, [Service] and [Project] lifecycle mechanism. The singleton
 * instance of this class will be automatically created if you use the `project.service<SyncPluginState>()` call.
 *
 * The persisted [PersistableState] is saved in the opened [Project]'s '.mps' folder with the name
 * [PluginStatePersister.DEFAULT_FILE_NAME]. The next time you open the [Project], the file will be loaded by MPS.
 *
 * @property project the active [Project] in MPS.
 *
 * @see [Service].
 * @see [State].
 * @see [PersistentStateComponent].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Service(Service.Level.PROJECT)
@State(
    name = "ModelixSyncPluginState",
    reloadable = true,
    storages = [Storage(PluginStatePersister.DEFAULT_FILE_NAME, roamingType = RoamingType.DISABLED)],
)
class SyncPluginState(private val project: Project) : PersistentStateComponent<PersistableState> {

    /**
     * The latest state of the plugin we have loaded from its persisted state (file).
     */
    var latestState: PersistableState? = null
        private set

    /**
     * @return the latest state of the plugin that contains all information needed to restore the active connections and
     * bindings to the model server the next time we start MPS.
     */
    override fun getState(): PersistableState {
        return PersistableState().fetchState(project)
    }

    /**
     * Sets [latestState] to [newState].
     */
    override fun loadState(newState: PersistableState) {
        latestState = newState
    }
}
