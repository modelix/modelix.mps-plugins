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
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.plugin.gui.ModelSyncGuiFactory.ModelSyncGui

/**
 * Keeps the [ModelSyncGui.bindingsModel] ComboBox in sync with the active [IBinding]s from the [BindingsRegistry].
 * I.e., newly activated [IBinding]s are added to the ComboBox, while the deactivated [IBinding]s are removed from it.
 *
 * The class is implemented as a [Thread], so it can be started separately in the background and is not disturbing the
 * MPS UI.
 *
 * @param project the active [Project] in MPS.
 *
 * @property pluginGui the MPS GUI of the modelix sync plugin.
 *
 * @see [Thread].
 * @see [Disposable].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class BindingsComboBoxRefresher(
    private val pluginGui: ModelSyncGui,
    project: Project,
) : Thread(), Disposable {

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * The registry to store the [IBinding]s.
     */
    private var bindingsRegistry = project.service<ServiceLocator>().bindingsRegistry

    /**
     * The comparator is used to sort the [IBinding]s in the correct order.
     */
    private val bindingsComparator = BindingSortComparator()

    /**
     * The [IBinding]s that are shown in the ComboBox.
     */
    private var existingBindings = LinkedHashSet<IBinding>()

    init {
        start()
    }

    /**
     * Waits for the new items in the [BindingsRegistry.changedBindings] queue, adds or removes the newly received
     * [IBinding] in the [existingBindings] collection, then sorts them in the correct order and finally populates the
     * [ModelSyncGui.bindingsModel] with the latest list of [IBinding]s.
     */
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

    /**
     * Interrupts the [Thread] to stop its operation.
     *
     * @see [run].
     */
    override fun dispose() {
        interrupt()
    }
}
