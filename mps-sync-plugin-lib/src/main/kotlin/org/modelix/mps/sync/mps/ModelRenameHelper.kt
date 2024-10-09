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

package org.modelix.mps.sync.mps

import jetbrains.mps.extapi.model.EditableSModelBase
import jetbrains.mps.extapi.persistence.FileDataSource
import jetbrains.mps.ide.refactoring.RenameModelDialog
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import jetbrains.mps.refactoring.Renamer
import jetbrains.mps.smodel.event.SModelListener
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * Utility class to rename or change the stereotype of a model.
 *
 * @property model the model whose name or stereotype we would like to change.
 * @property mpsProject the active [MPSProject].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelRenameHelper(private val model: EditableSModelBase, private val mpsProject: MPSProject) {

    /**
     * Just a normal logger to log messages.
     */
    val logger = KotlinLogging.logger {}

    /**
     * Change the stereotype of the model.
     *
     * @param stereotype the stereotype to be used.
     */
    fun changeStereotype(stereotype: String?) {
        val oldName = model.reference.name.withoutStereotype()
        val newName = SModelName(oldName.value, stereotype)
        rename(newName, false)
    }

    /**
     * Rename the model to a new name.
     *
     * @param modelName the new name of the model to be used.
     */
    fun renameModel(modelName: String) = rename(SModelName(modelName), model.source is FileDataSource)

    /**
     * Rename the model to a new name.
     *
     * The code is adopted from [RenameModelDialog.renameModel].
     *
     * @param newModelName the new name of the model.
     * @param changeFile if true, then the [SModelListener.modelFileChanged] method will be called after the model's
     * file is renamed.
     */
    private fun rename(newModelName: SModelName, changeFile: Boolean) {
        model.rename(newModelName.value, changeFile)
        updateModelAndModuleReferences()
        model.repository.saveAll()
    }

    /**
     * Updates the model and module references so they point to the correct model.
     *
     * The code is adopted from [Renamer.updateModelAndModuleReferences].
     */
    private fun updateModelAndModuleReferences() {
        mpsProject.modelAccess.checkWriteAccess()
        val var1: Iterator<*> = mpsProject.projectModulesWithGenerators.iterator()

        while (true) {
            var m: SModule
            do {
                do {
                    if (!var1.hasNext()) {
                        return
                    }

                    m = var1.next() as SModule
                } while (m !is AbstractModule)
            } while (m.isReadOnly)

            val module = m as AbstractModule
            module.updateExternalReferences()
            val var4: Iterator<*> = m.models.iterator()

            while (var4.hasNext()) {
                val sm: SModel = var4.next() as SModel
                if (!sm.isReadOnly) {
                    if (sm is EditableSModelBase && sm.updateExternalReferences(mpsProject.repository)) {
                        sm.isChanged = true
                    }
                }
            }
        }
    }
}
