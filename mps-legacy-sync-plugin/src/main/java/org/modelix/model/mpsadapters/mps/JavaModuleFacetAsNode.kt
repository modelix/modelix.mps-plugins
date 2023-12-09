package org.modelix.model.mpsadapters.mps

import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.vfs.IFile
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleFacet
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.PNodeAdapter.Companion.wrap
import org.modelix.model.area.IArea
import java.util.Objects

/*Generated by MPS */
class JavaModuleFacetAsNode(facet: JavaModuleFacet) : TreeElementAsNode<JavaModuleFacet>(facet) {
    private val generatedAccessor: IPropertyAccessor<JavaModuleFacet> =
        object : ReadOnlyPropertyAccessor<JavaModuleFacet>() {
            override fun get(element: JavaModuleFacet): String {
                // Based on this, I would expect the value to be always true, if not for some legacy code https://github.com/JetBrains/MPS/blob/2820965ff7b8836ed1d14adaf1bde29744c88147/core/project/source/jetbrains/mps/project/facets/JavaModuleFacetImpl.java
                return true.toString()
            }
        }
    private val pathAccessor: IPropertyAccessor<JavaModuleFacet> =
        object : ReadOnlyPropertyAccessor<JavaModuleFacet>() {
            override fun get(element: JavaModuleFacet): String? {
                if (element == null) {
                    throw IllegalStateException("The JavaModuleFacet should not be null")
                }
                val originalPath: String? = check_r9f4ri_a0b0a0a0d(element.classesGen)
                var moduleRoot: String? = null
                if (element.module is AbstractModule) {
                    val module: AbstractModule? = (element.module as AbstractModule?)
                    moduleRoot = check_r9f4ri_a0b0d0a0a0d(check_r9f4ri_a0a1a3a0a0a3(check_r9f4ri_a0a0b0d0a0a0d(module)))
                }
                var path: String? = originalPath
                if (moduleRoot != null && check_r9f4ri_a0f0a0a0d(originalPath, moduleRoot)) {
                    path = "\${module}" + originalPath!!.substring(moduleRoot.length)
                }
                return path
            }
        }

    override val concept: IConcept
        get() {
            return SConceptAdapter.wrap(CONCEPTS.`JavaModuleFacet$5E`)
        }

    override fun getPropertyAccessor(role: String): IPropertyAccessor<JavaModuleFacet>? {
        if (Objects.equals(role, PROPS.`generated$A44R`.name)) {
            return generatedAccessor
        }
        if (Objects.equals(role, PROPS.`path$A4yT`.name)) {
            return pathAccessor
        }
        return super.getPropertyAccessor(role)
    }

    override val roleInParent: String
        get() {
            return LINKS.`facets$vw9T`.name
        }

    override val parent: INode?
        get() {
            val module: SModule? = element.module
            return (if (module == null) null else SModuleAsNode(module))
        }

    override val reference: INodeReference
        get() {
            val module: SModule? = element.module
            return NodeReference(module!!.moduleReference)
        }

    class NodeReference(private val moduleReference: SModuleReference) : INodeReference {
        override fun serialize(): String {
            return "mps-java-facet:" + moduleReference
        }

        override fun resolveNode(area: IArea?): INode? {
            val module: SModule? = check_r9f4ri_a0a0e71(
                (
                    SModuleAsNode.NodeReference(
                        moduleReference,
                    ).resolveNode(area) as SModuleAsNode?
                    ),
            )
            if (module == null) {
                return null
            }
            val moduleFacet: SModuleFacet? = module.getFacetOfType(JavaModuleFacet.FACET_TYPE)
            return JavaModuleFacetAsNode(((moduleFacet as JavaModuleFacet?)!!))
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || this.javaClass != o.javaClass) {
                return false
            }
            val that: NodeReference = o as NodeReference
            return Objects.equals(moduleReference, that.moduleReference)
        }

        override fun hashCode(): Int {
            var result: Int = 0
            result = 31 * result + ((if (moduleReference != null) moduleReference.hashCode() else 0))
            return result
        }

        companion object {
            private fun check_r9f4ri_a0a0e71(checkedDotOperand: SModuleAsNode?): SModule? {
                if (null != checkedDotOperand) {
                    return checkedDotOperand.element
                }
                return null
            }
        }
    }

    private object CONCEPTS {
        /*package*/
        val `JavaModuleFacet$5E`: SConcept = MetaAdapterFactory.getConcept(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x1e9fde9535299166L,
            "org.modelix.model.repositoryconcepts.structure.JavaModuleFacet",
        )
    }

    private object PROPS {
        /*package*/
        val `generated$A44R`: SProperty = MetaAdapterFactory.getProperty(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x1e9fde9535299166L,
            0x1e9fde9535299167L,
            "generated",
        )

        /*package*/
        val `path$A4yT`: SProperty = MetaAdapterFactory.getProperty(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x1e9fde9535299166L,
            0x1e9fde9535299169L,
            "path",
        )
    }

    private object LINKS {
        /*package*/
        val `facets$vw9T`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x69652614fd1c50fL,
            0x1e9fde953529916cL,
            "facets",
        )
    }

    companion object {
        private fun check_r9f4ri_a0b0a0a0d(checkedDotOperand: IFile?): String? {
            if (null != checkedDotOperand) {
                return checkedDotOperand.path
            }
            return null
        }

        private fun check_r9f4ri_a0b0d0a0a0d(checkedDotOperand: IFile?): String? {
            if (null != checkedDotOperand) {
                return checkedDotOperand.path
            }
            return null
        }

        private fun check_r9f4ri_a0a1a3a0a0a3(checkedDotOperand: IFile?): IFile? {
            if (null != checkedDotOperand) {
                return checkedDotOperand.parent
            }
            return null
        }

        private fun check_r9f4ri_a0a0b0d0a0a0d(checkedDotOperand: AbstractModule?): IFile? {
            if (null != checkedDotOperand) {
                return checkedDotOperand.descriptorFile
            }
            return null
        }

        private fun check_r9f4ri_a0f0a0a0d(checkedDotOperand: String?, moduleRoot: String): Boolean {
            if (null != checkedDotOperand) {
                return checkedDotOperand.startsWith(moduleRoot)
            }
            return false
        }
    }
}
