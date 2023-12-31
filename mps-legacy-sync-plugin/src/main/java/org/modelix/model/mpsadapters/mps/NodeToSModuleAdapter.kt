package org.modelix.model.mpsadapters.mps

import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.module.SDependency
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleFacet
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleListener
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.ModelRoot
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter.Companion.wrap

/*Generated by MPS */
class NodeToSModuleAdapter protected constructor(private val node: INode, private val repository: SRepository?) :
    SModule {
    init {
        if (!(node.concept!!.isSubConceptOf(SConceptAdapter.Companion.wrap(CONCEPTS.`Module$4i`)))) {
            throw RuntimeException("Not a module: " + node.concept)
        }
    }

    override fun addModuleListener(listener: SModuleListener) {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun getDeclaredDependencies(): Iterable<SDependency> {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun <T : SModuleFacet?> getFacet(aClass: Class<T>): T? {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun getFacets(): Iterable<SModuleFacet> {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun getModel(id: SModelId): SModel? {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun getModelRoots(): Iterable<ModelRoot> {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun getModels(): Iterable<SModel> {
        val models: Iterable<INode> = node.getChildren(LINKS.`models$h3QT`.name)
        return Sequence.fromIterable<INode>(models).select<SModel>(object : ISelector<INode?, SModel?>() {
            override fun select(it: INode?): SModel? {
                val adapter: SModel? = NodeToSModelAdapter.Companion.wrap(it, repository)
                return adapter
            }
        })
    }

    override fun getModuleId(): SModuleId {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun getModuleName(): String? {
        return node.getPropertyValue(PROPS.`name$MnvL`.name)
    }

    override fun getModuleReference(): SModuleReference {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun getRepository(): SRepository? {
        return (if (repository != null) repository else MPSModuleRepository.getInstance())
    }

    override fun getUsedLanguages(): Set<SLanguage> {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun getUsedLanguageVersion(language: SLanguage): Int {
        throw UnsupportedOperationException("Not implemented")
    }

    override fun isPackaged(): Boolean {
        return false
    }

    override fun isReadOnly(): Boolean {
        return true
    }

    override fun removeModuleListener(listener: SModuleListener) {
        throw UnsupportedOperationException("Not implemented")
    }

    private object CONCEPTS {
        /*package*/
        val `Module$4i`: SConcept = MetaAdapterFactory.getConcept(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x69652614fd1c50fL,
            "org.modelix.model.repositoryconcepts.structure.Module",
        )
    }

    private object LINKS {
        /*package*/
        val `models$h3QT`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x69652614fd1c50fL,
            0x69652614fd1c512L,
            "models",
        )
    }

    private object PROPS {
        /*package*/
        val `name$MnvL`: SProperty = MetaAdapterFactory.getProperty(
            -0x3154ae6ada15b0deL,
            -0x646defc46a3573f4L,
            0x110396eaaa4L,
            0x110396ec041L,
            "name",
        )
    }

    companion object {
        fun wrap(node: INode?, repository: SRepository?): SModule? {
            if (node == null) {
                return null
            }
            return NodeToSModuleAdapter(node, repository)
        }
    }
}
