package org.modelix.model.mpsadapters.mps

import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.ITranslator2
import jetbrains.mps.internal.collections.runtime.LinkedListSequence
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReferenceLink
import org.modelix.model.api.PNodeAdapter.Companion.wrap
import org.modelix.model.area.IArea
import java.util.LinkedList
import java.util.Objects

/*Generated by MPS */
class SingleLanguageDependencyAsNode : INode {
    private var moduleReference: SModuleReference
    private var languageVersion: Int
    private var moduleImporter: SModule? = null
    private var modelImporter: SModel? = null

    constructor(moduleReference: SModuleReference, languageVersion: Int, importer: SModule?) {
        this.moduleReference = moduleReference
        this.languageVersion = languageVersion
        moduleImporter = importer
    }

    constructor(moduleReference: SModuleReference, languageVersion: Int, importer: SModel?) {
        this.moduleReference = moduleReference
        this.languageVersion = languageVersion
        modelImporter = importer
    }

    override fun getConceptReference(): IConceptReference {
        return concept!!.getReference()
    }

    fun getModuleReference(): SModuleReference {
        return moduleReference
    }

    fun getLanguageVersion(): Int {
        return languageVersion
    }

    override fun getArea(): IArea {
        return MPSArea()
    }

    override val isValid: Boolean
        get() {
            return true
        }

    override val reference: INodeReference
        get() {
            if (moduleImporter != null) {
                return NodeReference(moduleImporter!!.moduleReference, moduleReference.moduleId)
            }
            if (modelImporter != null) {
                return NodeReference(modelImporter!!.reference, moduleReference.moduleId)
            }
            throw IllegalStateException()
        }

    override val concept: IConcept
        get() {
            return SConceptAdapter.Companion.wrap(CONCEPTS.`SingleLanguageDependency$_9`)
        }

    override val roleInParent: String
        get() {
            if (moduleImporter != null) {
                return LINKS.`languageDependencies$vKlY`.name
            }
            if (modelImporter != null) {
                return LINKS.`usedLanguages$QK4E`.name
            }
            throw IllegalStateException()
        }

    override val parent: INode?
        get() {
            if (moduleImporter != null) {
                SModuleAsNode.Companion.wrap(moduleImporter)
            }
            if (modelImporter != null) {
                SModelAsNode.Companion.wrap(modelImporter)
            }
            throw IllegalStateException()
        }

    override fun getChildren(role: String?): Iterable<INode> {
        return LinkedListSequence.fromLinkedListNew(LinkedList())
    }

    override val allChildren: Iterable<INode>
        get() {
            val concept: IConcept? = concept
            if (concept == null) {
                return emptyList()
            }
            val links: Iterable<IChildLink> = concept.getAllChildLinks()
            return Sequence.fromIterable(links).select(object : ISelector<IChildLink, Iterable<INode>>() {
                override fun select(it: IChildLink): Iterable<INode> {
                    return getChildren(it.name)
                }
            }).translate(object : ITranslator2<Iterable<INode>, INode>() {
                override fun translate(it: Iterable<INode>): Iterable<INode> {
                    return it
                }
            })
        }

    override fun moveChild(string: String?, i: Int, node: INode) {
        throw UnsupportedOperationException()
    }

    override fun addNewChild(string: String?, i: Int, concept: IConcept?): INode {
        throw UnsupportedOperationException()
    }

    override fun addNewChild(string: String?, i: Int, reference: IConceptReference?): INode {
        throw UnsupportedOperationException()
    }

    override fun removeChild(node: INode) {
        throw UnsupportedOperationException()
    }

    override fun getReferenceTarget(role: String): INode? {
        return null
    }

    override fun getReferenceTargetRef(string: String): INodeReference? {
        return null
    }

    override fun setReferenceTarget(string: String, node: INode?) {
        throw UnsupportedOperationException()
    }

    override fun setReferenceTarget(string: String, reference: INodeReference?) {
        throw UnsupportedOperationException()
    }

    override fun getPropertyValue(propertyName: String): String? {
        if (Objects.equals(PROPS.`version$ApUL`.name, propertyName)) {
            return languageVersion.toString()
        } else if (Objects.equals(PROPS.`name$lpYq`.name, propertyName)) {
            return moduleReference.moduleName
        } else if (Objects.equals(PROPS.`uuid$lpJp`.name, propertyName)) {
            return moduleReference.moduleId.toString()
        } else {
            return null
        }
    }

    override fun setPropertyValue(string: String, string1: String?) {
        throw UnsupportedOperationException()
    }

    override fun getPropertyRoles(): List<String> {
        val concept: IConcept? = concept
        if (concept == null) {
            return emptyList()
        }
        val allProperties: List<IProperty> = concept.getAllProperties()
        return ListSequence.fromList(allProperties).select(object : ISelector<IProperty, String>() {
            override fun select(it: IProperty): String {
                return it.name
            }
        }).toListSequence()
    }

    override fun getReferenceRoles(): List<String> {
        val concept: IConcept? = concept
        if (concept == null) {
            return emptyList()
        }
        val allReferenceLinks: List<IReferenceLink> = concept.getAllReferenceLinks()
        return ListSequence.fromList(allReferenceLinks).select(object : ISelector<IReferenceLink, String>() {
            override fun select(it: IReferenceLink): String {
                return it.name
            }
        }).toListSequence()
    }

    class NodeReference : INodeReference {
        private var userModuleReference: SModuleReference? = null
        private var userModel: SModelReference? = null
        private var usedModuleId: SModuleId?

        constructor(userModuleReference: SModuleReference?, usedModuleId: SModuleId?) {
            this.userModuleReference = userModuleReference
            this.usedModuleId = usedModuleId
        }

        constructor(userModel: SModelReference?, usedModuleId: SModuleId?) {
            this.userModel = userModel
            this.usedModuleId = usedModuleId
        }

        override fun serialize(): String {
            if (userModuleReference == null) {
                return "mps-lang:" + usedModuleId + "#IN#" + userModel
            } else {
                return "mps-lang:" + usedModuleId + "#IN#" + userModuleReference
            }
        }

        override fun resolveNode(area: IArea?): INode? {
            var repo: SRepository? = null
            if (area != null) {
                val areas: List<IArea> = area.collectAreas()
                repo = areas.filterIsInstance<MPSArea>().map { it.repository }.filterNotNull().firstOrNull()
            }
            if (repo == null) {
                repo = MPSModuleRepository.getInstance()
            }
            if (userModuleReference != null) {
                val user: SModule? = userModuleReference!!.resolve((repo)!!)
                if (user == null) {
                    return null
                }
                return SModuleAsNode(user).findSingleLanguageDependency(usedModuleId)
            } else if (userModel != null) {
                val model: SModel = userModel!!.resolve(repo)
                return SModelAsNode(model).findSingleLanguageDependency(usedModuleId)
            } else {
                throw IllegalStateException()
            }
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || this.javaClass != o.javaClass) {
                return false
            }
            val that: NodeReference = o as NodeReference
            if (Objects.equals(userModuleReference, that.userModuleReference)) {
                return false
            }
            if (Objects.equals(userModel, that.userModel)) {
                return false
            }
            if (Objects.equals(usedModuleId, that.usedModuleId)) {
                return false
            }
            return true
        }

        override fun hashCode(): Int {
            var result: Int = 0
            result = 31 * result + ((if (userModuleReference != null) (userModuleReference as Any).hashCode() else 0))
            result = 11 * result + ((if (usedModuleId != null) (usedModuleId as Any).hashCode() else 0))
            result = 37 * result + ((if (userModel != null) (userModel as Any).hashCode() else 0))
            return result
        }
    }

    private object CONCEPTS {
        /*package*/
        val `SingleLanguageDependency$_9`: SConcept = MetaAdapterFactory.getConcept(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x1e9fde953529917dL,
            "org.modelix.model.repositoryconcepts.structure.SingleLanguageDependency",
        )
    }

    private object LINKS {
        /*package*/
        val `languageDependencies$vKlY`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x69652614fd1c50fL,
            0x1e9fde9535299187L,
            "languageDependencies",
        )

        /*package*/
        val `usedLanguages$QK4E`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x69652614fd1c50cL,
            0x4aaf28cf2092e98eL,
            "usedLanguages",
        )
    }

    private object PROPS {
        /*package*/
        val `name$lpYq`: SProperty = MetaAdapterFactory.getProperty(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x7c527144386aca0fL,
            0x7c527144386aca13L,
            "name",
        )

        /*package*/
        val `uuid$lpJp`: SProperty = MetaAdapterFactory.getProperty(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x7c527144386aca0fL,
            0x7c527144386aca12L,
            "uuid",
        )

        /*package*/
        val `version$ApUL`: SProperty = MetaAdapterFactory.getProperty(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x1e9fde953529917dL,
            0x1e9fde9535299183L,
            "version",
        )
    }
}
