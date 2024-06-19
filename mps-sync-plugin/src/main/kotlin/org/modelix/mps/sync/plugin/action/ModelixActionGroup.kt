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
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.plugin.icons.CloudIcons

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelixActionGroup : ActionGroup("Modelix Actions", "", CloudIcons.PLUGIN_ICON) {

    private val modelActions = arrayOf(ModelSyncAction, UnbindModelAction)
    private val moduleActions = arrayOf(ModuleSyncAction, UnbindModuleAction)

    init {
        isPopup = true
    }

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        val model = event?.getData(ModelSyncAction.contextModel)
        if (model != null) {
            return modelActions
        }

        val module = event?.getData(ModuleSyncAction.contextModule)
        if (module != null) {
            return moduleActions
        }

        return emptyArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModelixActionGroup

        if (!modelActions.contentEquals(other.modelActions)) return false
        if (!moduleActions.contentEquals(other.moduleActions)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = modelActions.contentHashCode()
        result = 31 * result + moduleActions.contentHashCode()
        return result
    }
}
