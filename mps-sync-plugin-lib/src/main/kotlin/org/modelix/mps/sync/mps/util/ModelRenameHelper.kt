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

package org.modelix.mps.sync.mps.util

import jetbrains.mps.extapi.model.EditableSModelBase
import jetbrains.mps.extapi.persistence.FileDataSource
import jetbrains.mps.project.AbstractModule
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
class ModelRenameHelper(private val model: EditableSModelBase) {

    val logger = KotlinLogging.logger {}

    fun changeStereotype(stereotype: String?) {
        val oldName = model.reference.name.withoutStereotype()
        val newName = SModelName(oldName.value, stereotype)
        rename(newName, false)
    }

    fun renameModel(modelName: String) = rename(SModelName(modelName), model.source is FileDataSource)

    // adopted from jetbrains.mps.ide.refactoring.RenameModelDialog.renameModel
    private fun rename(newModelName: SModelName, changeFile: Boolean) {
        model.rename(newModelName.value, changeFile)
        updateModelAndModuleReferences()
        model.repository.saveAll()
    }

    // adopted from Renamer.updateModelAndModuleReferences(project) in MPS 2023.2
    private fun updateModelAndModuleReferences() {
        val mpsProject = ActiveMpsProjectInjector.activeMpsProject!!
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
