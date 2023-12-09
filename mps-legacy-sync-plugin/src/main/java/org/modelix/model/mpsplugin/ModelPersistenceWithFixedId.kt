package org.modelix.model.mpsplugin

import jetbrains.mps.extapi.model.SModelData
import jetbrains.mps.persistence.DefaultModelPersistence
import jetbrains.mps.persistence.LazyLoadFacility
import jetbrains.mps.smodel.DefaultSModel
import jetbrains.mps.smodel.DefaultSModelDescriptor
import jetbrains.mps.smodel.SModelHeader
import jetbrains.mps.smodel.loading.ModelLoadResult
import jetbrains.mps.smodel.loading.ModelLoadingState
import jetbrains.mps.smodel.persistence.def.ModelPersistence
import jetbrains.mps.smodel.persistence.def.ModelReadException
import org.jetbrains.mps.openapi.model.SModel
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
import java.io.IOException

/*Generated by MPS */
/**
 * Uses the provided model ID instead of SModelId.generate().
 * Everything else is just copied from DefaultModelPersistence.
 */
open class ModelPersistenceWithFixedId(private val moduleRef: SModuleReference, private val modelId: SModelId?) :
    DefaultModelPersistence() {
    @Throws(UnsupportedDataSourceException::class)
    override fun create(
        dataSource: DataSource,
        modelName: SModelName,
        vararg options: ModelLoadingOption,
    ): SModel {
        if (!((supports(dataSource)))) {
            throw UnsupportedDataSourceException(dataSource)
        }
        val header: SModelHeader = SModelHeader.create(ModelPersistence.LAST_VERSION)
        val modelReference: SModelReference = PersistenceFacade.getInstance().createModelReference(
            moduleRef,
            (modelId)!!,
            modelName.value,
        )
        header.modelReference = modelReference
        val rv: DefaultSModelDescriptor =
            DefaultSModelDescriptor(PersistenceFacility(this, dataSource as StreamDataSource?), header)
        if (dataSource.getTimestamp() != -1L) {
            rv.replace(DefaultSModel(modelReference, header))
        }
        return rv
    }

    private class PersistenceFacility(modelFactory: DefaultModelPersistence?, dataSource: StreamDataSource?) :
        LazyLoadFacility(
            (modelFactory)!!,
            (dataSource)!!,
            true,
        ) {
        private val source0: StreamDataSource
            private get() {
                return super.getSource() as StreamDataSource
            }

        @Throws(ModelReadException::class)
        override fun readHeader(): SModelHeader {
            return ModelPersistence.loadDescriptor(source0)
        }

        @Throws(ModelReadException::class)
        override fun readModel(header: SModelHeader, state: ModelLoadingState): ModelLoadResult {
            return ModelPersistence.readModel(header, source0, state)
        }

        override fun doesSaveUpgradePersistence(header: SModelHeader): Boolean {
            // not sure !=-1 is really needed, just left to be ensured about compatibility
            return header.persistenceVersion != ModelPersistence.LAST_VERSION && header.persistenceVersion != -1
        }

        @Throws(IOException::class)
        override fun saveModel(header: SModelHeader, modelData: SModelData) {
            try {
                ModelPersistence.saveModel(
                    (modelData as jetbrains.mps.smodel.SModel?)!!,
                    source0,
                    header.persistenceVersion,
                )
            } catch (e: ModelSaveException) {
                throw RuntimeException(e)
            }
        }
    }
}
