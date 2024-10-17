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

package org.modelix.mps.sync.plugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import jetbrains.mps.extapi.model.SModelBase
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.bindings.ModelBinding

/**
 * An MPS action to synchronize an [SModel] to the model server. The action is registered in the MPS UI and thereby
 * is triggered by the user.
 *
 * @see [AnAction].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Suppress("ComponentNotRegistered")
object ModelSyncAction : AnAction("Synchronize Model to Server") {

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * Synchronizes the [SModel] that is in the [event] to the model server and activates its [ModelBinding].
     *
     * @param event the event from MPS, to fetch the [SModel] from it.
     *
     * @see [AnAction.actionPerformed].
     */
    override fun actionPerformed(event: AnActionEvent) =
        actionPerformedSafely(event, logger, "Model synchronization error occurred.") { serviceLocator ->
            val model = event.getData(getMpsContextModelDataKey()) as? SModelBase
            checkNotNull(model) { "Synchronization is not possible, because Model (${model?.name}) is not an SModelBase." }
            check(!model.isReadOnly) { "Synchronization action is not allowed on read-only Model (${model.name})." }

            val branchRegistry = serviceLocator.branchRegistry
            val branch = branchRegistry.getBranch()
            checkNotNull(branch) { "Connect to a server and branch before synchronizing a Model." }

            val syncService = serviceLocator.syncService
            val binding = syncService.bindModelFromMps(model, branch)
            binding.activate()
        }
}
