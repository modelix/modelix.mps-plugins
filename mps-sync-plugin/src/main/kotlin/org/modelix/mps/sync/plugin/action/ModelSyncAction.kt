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
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.workbench.MPSDataKeys
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.modelix.BranchRegistry
import org.modelix.mps.sync.mps.notifications.InjectableNotifierWrapper
import org.modelix.mps.sync.plugin.ModelSyncService

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelSyncAction : AnAction {

    companion object {
        val CONTEXT_MODEL = DataKey.create<SModel>("MPS_Context_SModel")

        fun create() = ModelSyncAction("Synchronize model to server")
    }

    private val logger = KotlinLogging.logger {}
    private val notifierInjector = InjectableNotifierWrapper

    constructor() : super()

    constructor(text: String) : super(text)

    override fun actionPerformed(event: AnActionEvent) {
        var modelName = ""
        try {
            val model = event.getData(CONTEXT_MODEL) as? SModelBase
            checkNotNull(model) { "Synchronization is not possible, because Model is not an SModelBase." }
            modelName = model.name.simpleName

            val branch = BranchRegistry.branch
            checkNotNull(branch) { "Connect to a server and branch before synchronizing a model." }

            val project = event.getData(MPSDataKeys.PROJECT)
            val syncService = project?.service<ModelSyncService>()
            checkNotNull(syncService) { "Synchronization is not possible, because SyncService is null." }

            val binding = syncService.bindModelFromMps(model, branch)
            binding.activate()
        } catch (t: Throwable) {
            val message = "Model '$modelName' synchronization error occurred. Cause: ${t.message}"
            notifierInjector.notifyAndLogError(message, t, logger)
        }
    }
}
