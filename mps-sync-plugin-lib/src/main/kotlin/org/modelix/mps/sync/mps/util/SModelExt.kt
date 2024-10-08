/*
 * Copyright (c) 2023.
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

import jetbrains.mps.extapi.model.SModelDescriptorStub
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * Checks if the parameter DevKit module reference is an [SModelDescriptorStub]. If so, then it adds the reference to
 * the model as a DevKit. Otherwise, it throws an exception.
 *
 * @param devKitModuleReference the DevKit module reference to be added to the model.
 *
 * @throws IllegalArgumentException if the parameter is not an [SModelDescriptorStub].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Throws(IllegalArgumentException::class)
internal fun SModel.addDevKit(devKitModuleReference: SModuleReference) {
    require(this is SModelDescriptorStub) { "Model ${this.modelId} must be an SModelDescriptorStub" }
    this.addDevKit(devKitModuleReference)
}

/**
 * Checks if the parameter DevKit module reference is an [SModelDescriptorStub]. If so, then it removes the reference
 * from the model's DevKit dependencies. Otherwise, it throws an exception.
 *
 * @param devKitModuleReference the DevKit module reference to be removed from the model.
 *
 * @throws IllegalArgumentException if the parameter is not an [SModelDescriptorStub].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Throws(IllegalArgumentException::class)
internal fun SModel.deleteDevKit(devKitModuleReference: SModuleReference) {
    require(this is SModelDescriptorStub) { "Model ${this.modelId} must be an SModelDescriptorStub" }
    this.deleteDevKit(devKitModuleReference)
}

/**
 * Checks if the parameter [SLanguage] is an [SModelDescriptorStub]. If so, then it adds the language with the given
 * version to the model as a language import. Otherwise, it throws an exception.
 *
 * @param sLanguage the language import to be added to the model.
 * @param version the version number of the language.
 *
 * @throws IllegalArgumentException if the parameter language is not an [SModelDescriptorStub].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal fun SModel.addLanguageImport(sLanguage: SLanguage, version: Int) {
    require(this is SModelDescriptorStub) { "Model ${this.modelId} must be an SModelDescriptorStub" }
    this.addLanguage(sLanguage)
    this.setLanguageImportVersion(sLanguage, version)
}

/**
 * Checks if the parameter [SLanguage] is an [SModelDescriptorStub]. If so, then it removes the language import from
 * the model. Otherwise, it throws an exception.
 *
 * @param sLanguage the language import to be removed from the model.
 *
 * @throws IllegalArgumentException if the parameter language is not an [SModelDescriptorStub].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal fun SModel.deleteLanguage(sLanguage: SLanguage) {
    require(this is SModelDescriptorStub) { "Model ${this.modelId} must be an SModelDescriptorStub" }
    this.deleteLanguageId(sLanguage)
}

/**
 * @return [SModel.getModelId] as String.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal fun SModel.getModelixId() = this.modelId.toString()

/**
 * @return true if the model's name eds with [descriptorSuffix].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal fun SModel.isDescriptorModel() = this.name.value.endsWith(descriptorSuffix)

/**
 * Descriptor models have this string in their name.
 */
internal const val descriptorSuffix = "@descriptor"
