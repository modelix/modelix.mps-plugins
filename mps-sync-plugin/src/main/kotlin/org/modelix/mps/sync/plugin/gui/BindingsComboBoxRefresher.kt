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

package org.modelix.mps.sync.plugin.gui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.BindingLifecycleState
import org.modelix.mps.sync.bindings.BindingSortComparator
import org.modelix.mps.sync.mps.services.ServiceLocator

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class BindingsComboBoxRefresher(
    private val pluginGui: ModelSyncGuiFactory.ModelSyncGui,
    project: Project,
) : Thread(), Disposable {

    private val logger = KotlinLogging.logger {}

    private var bindingsRegistry = project.service<ServiceLocator>().bindingsRegistry

    private val bindingsComparator = BindingSortComparator()
    private var existingBindings = LinkedHashSet<IBinding>()

    init {
        start()
    }

    override fun run() {
        try {
            while (!isInterrupted) {
                val bindingState = bindingsRegistry.changedBindings.take()
                val binding = bindingState.binding

                when (bindingState.state) {
                    BindingLifecycleState.ACTIVATE -> existingBindings.add(binding)
                    BindingLifecycleState.REMOVE -> existingBindings.remove(binding)
                    else -> {}
                }

                val sorted = existingBindings.sortedWith(bindingsComparator)
                pluginGui.populateBindingCB(sorted)
            }
        } catch (ignored: InterruptedException) {
            logger.warn { "BindingsComboBoxRefresher is shutting down. If the corresponding project is closing, then it's a normal behavior." }
        }
    }

    override fun dispose() {
        interrupt()
    }
}
