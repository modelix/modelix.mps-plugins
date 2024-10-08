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
import com.intellij.openapi.actionSystem.DataKey
import jetbrains.mps.extapi.model.SModelBase
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Suppress("ComponentNotRegistered")
object UnbindModelAction : AnAction("Unbind Model") {

    private val contextModel = DataKey.create<SModel>("MPS_Context_SModel")

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    override fun actionPerformed(event: AnActionEvent) =
        actionPerformedSafely(event, logger, "Model unbind error occurred.") { serviceLocator ->
            val model = event.getData(contextModel) as? SModelBase
            checkNotNull(model) { "Unbinding is not possible, because Model (${model?.name}) is not an SModelBase." }
            check(!model.isReadOnly) { "Unbinding is not possible, because Model (${model.name}) is read-only." }

            val bindingsRegistry = serviceLocator.bindingsRegistry
            val binding = bindingsRegistry.getModelBinding(model)
            checkNotNull(binding) { "Model is not synchronized to the server yet." }

            binding.deactivate(removeFromServer = false)
        }
}
