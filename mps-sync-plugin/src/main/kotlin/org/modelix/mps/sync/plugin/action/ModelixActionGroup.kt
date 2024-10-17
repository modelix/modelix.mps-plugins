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

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.plugin.icons.CloudIcons

/**
 * A group of MPS actions we want to register in the context menu of the [SModel]s and [SModule]s of the [Project].
 * We register the action group by hand, thus we do not need it in the plugin.xml.
 *
 * @see [ActionGroup].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Suppress("ComponentNotRegistered")
object ModelixActionGroup : ActionGroup("Modelix Actions", "", CloudIcons.PLUGIN_ICON) {

    /**
     * The actions we want to register for hte [SModel]s.
     */
    private val modelActions = arrayOf(ModelSyncAction, UnbindModelAction)

    /**
     * The actions we want to register for hte [SModule]s.
     */
    private val moduleActions = arrayOf(ModuleSyncAction, UnbindModuleAction)

    init {
        // We want to show the actions in a sub context menu.
        isPopup = true
    }

    /**
     * @param event the event from MPS.
     *
     * @return [modelActions] if we are in the context menu of an [SModel], or the [moduleActions] if we are in the
     * context menu of an [SModule]. Otherwise, an empty array.
     *
     * @see [ActionGroup.getChildren].
     */
    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        val model = event?.getData(getMpsContextModelDataKey())
        if (model != null) {
            return modelActions
        }

        val module = event?.getData(getMpsContextModuleDataKey())
        if (module != null) {
            return moduleActions
        }

        return emptyArray()
    }
}
