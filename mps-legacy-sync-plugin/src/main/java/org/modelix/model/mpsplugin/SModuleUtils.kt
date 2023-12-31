package org.modelix.model.mpsplugin

import jetbrains.mps.internal.collections.runtime.IWhereFilter
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.persistence.DefaultModelRoot
import jetbrains.mps.smodel.SModelStereotype
import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.ModelRoot

/*Generated by MPS */
object SModuleUtils {
    private val LOG: Logger = LogManager.getLogger(SModuleUtils::class.java)
    fun createModel(_this: SModule?, name: String?, id: SModelId?): SModel {
        val modelRoots: Iterable<ModelRoot> = _this!!.modelRoots
        val modelName: SModelName = SModelName((name)!!)
        val modelRoot: DefaultModelRoot = Sequence.fromIterable<ModelRoot>(modelRoots).ofType<DefaultModelRoot>(
            DefaultModelRoot::class.java,
        ).findFirst(object : IWhereFilter<DefaultModelRoot>() {
            override fun accept(it: DefaultModelRoot): Boolean {
                return it.canCreateModel(modelName)
            }
        })
        return modelRoot.createModel(
            modelName,
            null,
            null,
            ModelPersistenceWithFixedId(_this.moduleReference, id),
        )
    }

    fun getModelsWithoutDescriptor(_this: SModule?): List<SModel> {
        val models: Iterable<SModel> = _this!!.models
        return Sequence.fromIterable(models).where(object : IWhereFilter<SModel?>() {
            override fun accept(it: SModel?): Boolean {
                return !(SModelStereotype.isDescriptorModel(it))
            }
        }).toListSequence()
    }
}
