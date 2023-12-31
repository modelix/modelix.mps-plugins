package org.modelix.model.mpsplugin

import jetbrains.mps.internal.collections.runtime.IListSequence
import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SLinkOperations
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SPropertyOperations
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.area.PArea
import org.modelix.model.lazy.RepositoryId

/*Generated by MPS */
object ModelServerNavigation {
    fun trees(_this: ModelServerConnection): List<CloudRepository> {
        val info: SNode? = _this.info
        return PArea(_this.infoBranch!!).executeRead<IListSequence<CloudRepository>>({
            ListSequence.fromList(SLinkOperations.getChildren(info, LINKS.`repositories$b56J`))
                .select(object : ISelector<SNode?, CloudRepository>() {
                    override fun select(it: SNode?): CloudRepository {
                        val repositoryId: RepositoryId =
                            RepositoryId(SPropertyOperations.getString(it, PROPS.`id$baYB`))
                        return CloudRepository(_this, repositoryId)
                    }
                }).toListSequence()
        })
    }

    private object LINKS {
        /*package*/
        val `repositories$b56J`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            -0x4967f1420fe2ba63L,
            -0x56adc78bf09cec4cL,
            0x62b7d9b07cecbcbfL,
            0x62b7d9b07cecbcc2L,
            "repositories",
        )
    }

    private object PROPS {
        /*package*/
        val `id$baYB`: SProperty = MetaAdapterFactory.getProperty(
            -0x4967f1420fe2ba63L,
            -0x56adc78bf09cec4cL,
            0x62b7d9b07cecbcc0L,
            0x62b7d9b07cecbcc6L,
            "id",
        )
    }
}
