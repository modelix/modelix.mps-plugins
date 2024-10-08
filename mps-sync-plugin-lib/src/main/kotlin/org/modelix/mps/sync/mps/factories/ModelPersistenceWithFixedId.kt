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

package org.modelix.mps.sync.mps.factories

import jetbrains.mps.extapi.model.SModelData
import jetbrains.mps.persistence.DefaultModelPersistence
import jetbrains.mps.persistence.LazyLoadFacility
import jetbrains.mps.smodel.DefaultSModel
import jetbrains.mps.smodel.DefaultSModelDescriptor
import jetbrains.mps.smodel.SModel
import jetbrains.mps.smodel.SModelHeader
import jetbrains.mps.smodel.loading.ModelLoadingState
import jetbrains.mps.smodel.persistence.def.ModelPersistence
import jetbrains.mps.smodel.persistence.def.ModelReadException
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.persistence.DataSource
import org.jetbrains.mps.openapi.persistence.ModelLoadingOption
import org.jetbrains.mps.openapi.persistence.ModelSaveException
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.jetbrains.mps.openapi.persistence.StreamDataSource
import org.jetbrains.mps.openapi.persistence.UnsupportedDataSourceException
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * A factory to create [org.jetbrains.mps.openapi.model.SModel] in the file system.
 *
 * @property moduleRef the parent module's reference.
 * @property modelId the ID of the [org.jetbrains.mps.openapi.model.SModel] to be created.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelPersistenceWithFixedId(private val moduleRef: SModuleReference, private val modelId: SModelId) :
    DefaultModelPersistence() {

    /**
     * Uses the provided model ID instead of [jetbrains.mps.smodel.SModelId.generate]. Everything else is just copied
     * from [DefaultModelPersistence].
     *
     * @see [DefaultModelPersistence.create]
     */
    @Throws(UnsupportedDataSourceException::class)
    override fun create(
        dataSource: DataSource,
        modelName: SModelName,
        vararg options: ModelLoadingOption,
    ): org.jetbrains.mps.openapi.model.SModel {
        if (!supports(dataSource)) {
            throw UnsupportedDataSourceException(dataSource)
        }
        val header = SModelHeader.create(ModelPersistence.LAST_VERSION)
        val modelReference: SModelReference =
            PersistenceFacade.getInstance().createModelReference(moduleRef, modelId, modelName.value)
        header.modelReference = modelReference
        val rv = DefaultSModelDescriptor(ModelPersistenceFacility(this, dataSource as StreamDataSource), header)
        if (dataSource.getTimestamp() != -1L) {
            rv.replace(DefaultSModel(modelReference, header))
        }
        return rv
    }
}

/**
 * Persist the model in the referred data source.
 *
 * @param modelFactory the factory to create a model.
 *
 * @property dataSource the access to the data store (e.g. file system), where the model will be persisted.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelPersistenceFacility(modelFactory: DefaultModelPersistence, private val dataSource: StreamDataSource) :
    LazyLoadFacility(modelFactory, dataSource, true) {

    @Throws(ModelReadException::class)
    override fun readHeader() = ModelPersistence.loadDescriptor(dataSource)

    @Throws(ModelReadException::class)
    override fun readModel(header: SModelHeader, state: ModelLoadingState) =
        ModelPersistence.readModel(header, dataSource, state)

    override fun doesSaveUpgradePersistence(header: SModelHeader): Boolean {
        // not sure !=-1 is really needed, just left to be ensured about compatibility
        return header.persistenceVersion != ModelPersistence.LAST_VERSION && header.persistenceVersion != -1
    }

    @Throws(ModelSaveException::class)
    override fun saveModel(header: SModelHeader, modelData: SModelData) {
        ModelPersistence.saveModel(modelData as SModel, dataSource, header.persistenceVersion)
    }
}
