package org.modelix.model.mpsadapters.mps

import jetbrains.mps.baseLanguage.closures.runtime.Wrappers._T
import jetbrains.mps.smodel.GlobalModelAccess
import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.smodel.adapter.ids.SConceptId
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.language.ConceptRegistry
import jetbrains.mps.smodel.runtime.ConceptDescriptor
import jetbrains.mps.smodel.runtime.illegal.IllegalConceptDescriptor
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.ModelAccess
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IBranch
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.PNodeAdapter.Companion.wrap
import org.modelix.model.area.IArea
import org.modelix.model.area.IAreaListener
import org.modelix.model.area.IAreaReference
import java.util.Objects

/*Generated by MPS */
class MPSArea @JvmOverloads constructor(repository: SRepository? = MPSModuleRepository.getInstance()) :
    IArea,
    IAreaReference {
    val repository: SRepository = repository ?: MPSModuleRepository.getInstance()

    public override fun getLockOrderingPriority(): Long {
        return 100L shl 32
    }

    public override fun getReference(): IAreaReference {
        return this
    }

    public override fun resolveConcept(reference: IConceptReference): IConcept? {
        if (!(reference is ConceptReference)) {
            return null
        }
        var uid: String = reference.uid
        if (uid.startsWith("mps:")) {
            uid = uid.substring(4)
        }
        val conceptId: SConceptId?
        try {
            conceptId = SConceptId.deserialize(uid)
        } catch (ex: Exception) {
            return null
        }
        if (conceptId == null) {
            return null
        }
        val conceptDescriptor: ConceptDescriptor = ConceptRegistry.getInstance().getConceptDescriptor(conceptId)
        if (conceptDescriptor is IllegalConceptDescriptor) {
            return null
        }
        return SConceptAdapter.Companion.wrap(MetaAdapterFactory.getAbstractConcept(conceptDescriptor))
    }

    public override fun resolveArea(reference: IAreaReference): IArea? {
        return (if (Objects.equals(reference, this)) this else null)
    }

    public override fun canRead(): Boolean {
        return repository!!.getModelAccess().canRead()
    }

    public override fun canWrite(): Boolean {
        return repository!!.getModelAccess().canWrite()
    }

    public override fun <T> executeRead(f: () -> T): T {
        val result: _T<T> = _T()
        repository!!.getModelAccess().runReadAction(object : Runnable {
            public override fun run() {
                result.value = f.invoke()
            }
        })
        return result.value
    }

    public override fun <T> executeWrite(f: () -> T): T {
        val result: _T<T> = _T()
        val modelAccess: ModelAccess = repository!!.getModelAccess()
        if (modelAccess is GlobalModelAccess) {
            modelAccess.runWriteAction(object : Runnable {
                public override fun run() {
                    result.value = f.invoke()
                }
            })
        } else {
            modelAccess.executeCommand(object : Runnable {
                public override fun run() {
                    result.value = f.invoke()
                }
            })
        }
        return result.value
    }

    public override fun getRoot(): INode {
        return SRepositoryAsNode(repository)
    }

    public override fun addListener(listener: IAreaListener) {
        throw UnsupportedOperationException("Not implemented yet")
    }

    public override fun removeListener(listener: IAreaListener) {
        throw UnsupportedOperationException("Not implemented yet")
    }

    public override fun resolveNode(reference: INodeReference): INode? {
        return resolveOriginalNode(reference)
    }

    public override fun resolveOriginalNode(reference: INodeReference): INode? {
        if (reference is SNodeReferenceAdapter) {
            val mpsNode: SNode? = reference.getReference()!!
                .resolve(
                    repository,
                )
            return SNodeToNodeAdapter.Companion.wrap(mpsNode)
        }
        return null
    }

    public override fun resolveBranch(id: String): IBranch? {
        return null
    }

    public override fun collectAreas(): List<IArea> {
        return listOf<IArea>(this)
    }

    public override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || this.javaClass != o.javaClass) {
            return false
        }
        val that: MPSArea = o as MPSArea
        if ((if (repository != null) !((repository == that.repository)) else that.repository != null)) {
            return false
        }
        return true
    }

    public override fun hashCode(): Int {
        var result: Int = 0
        result = 31 * result + ((if (repository != null) (repository as Any).hashCode() else 0))
        return result
    }
}
