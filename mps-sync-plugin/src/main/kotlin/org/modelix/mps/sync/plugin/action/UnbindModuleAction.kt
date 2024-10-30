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

package org.modelix.mps.sync.plugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import jetbrains.mps.project.AbstractModule
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.bindings.ModuleBinding

/**
 * An MPS action to deactivate the [ModuleBinding] of an [SModel] and therefore stop the MPS Module's synchronization to
 * the model server. The action is registered in the MPS UI and thereby is triggered by the user.
 *
 * @see [AnAction].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Suppress("ComponentNotRegistered")
object UnbindModuleAction : AnAction("Unbind Module") {

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * Deactivates the [ModuleBinding] of the [SModule] that is in the [event] and therefore stops the MPS Module's and
     * its MPS Models' synchronization to the model server. I.e., it deactivates the contained MPS Models'
     * [ModelBinding]s too.
     *
     * @param event the event from MPS, to fetch the [SModule] from it.
     *
     * @see [AnAction.actionPerformed].
     */
    override fun actionPerformed(event: AnActionEvent) =
        actionPerformedSafely(event, logger, "Module unbind error occurred.") { serviceLocator ->
            val module = event.getData(getMpsContextModuleDataKey()) as? AbstractModule
            checkNotNull(module) { "Unbinding is not possible, because Module (${module?.moduleName}) is not an AbstractModule." }
            check(!module.isReadOnly) { "Unbinding is not possible, because Module (${module.moduleName}) is read-only." }

            val bindingsRegistry = serviceLocator.bindingsRegistry
            val binding = bindingsRegistry.getModuleBinding(module)
            checkNotNull(binding) { "Module is not synchronized to the server yet." }

            binding.deactivate(removeFromServer = false)
        }
}
