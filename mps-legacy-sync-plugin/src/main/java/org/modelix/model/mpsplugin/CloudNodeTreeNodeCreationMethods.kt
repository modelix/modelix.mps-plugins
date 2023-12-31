package org.modelix.model.mpsplugin

import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PNodeAdapter.Companion.wrap
import org.modelix.model.area.PArea
import org.modelix.model.mpsadapters.mps.SConceptAdapter
import org.modelix.model.mpsplugin.history.CloudNodeTreeNode

/*Generated by MPS */
object CloudNodeTreeNodeCreationMethods {
    fun createProject(_this: CloudNodeTreeNode, moduleName: String?): INode {
        // TODO check this represent a repository/a tree root
        return PNodeAdapterCreationMethods.createProject((_this.node as PNodeAdapter?), moduleName)
    }

    fun createModule(_this: CloudNodeTreeNode, moduleName: String?): INode {
        // TODO check this represent a repository/a tree root
        return PNodeAdapterCreationMethods.createModuleInRepository((_this.node as PNodeAdapter?), moduleName)
    }

    fun createModel(_this: CloudNodeTreeNode, modelName: String?): INode {
        // TODO check this represent a module
        return PArea(_this.branch).executeWrite<INode>({
            val newModel: INode = _this.node.addNewChild(
                LINKS.`models$h3QT`.name,
                -1,
                SConceptAdapter.Companion.wrap(
                    CONCEPTS.`Model$2P`,
                ),
            )
            newModel.setPropertyValue(PROPS.`name$MnvL`.name, modelName)
            newModel
        })
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

    private object CONCEPTS {
        /*package*/
        val `Model$2P`: SConcept = MetaAdapterFactory.getConcept(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x69652614fd1c50cL,
            "org.modelix.model.repositoryconcepts.structure.Model",
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
}
