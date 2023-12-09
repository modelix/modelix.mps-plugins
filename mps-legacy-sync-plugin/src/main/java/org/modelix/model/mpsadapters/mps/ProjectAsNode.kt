package org.modelix.model.mpsadapters.mps

import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.IWhereFilter
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.Project
import jetbrains.mps.project.ProjectManager
import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.PNodeAdapter.Companion.wrap
import org.modelix.model.area.IArea
import java.util.Objects

/*Generated by MPS */
class ProjectAsNode(element: MPSProject) : TreeElementAsNode<MPSProject>(element) {
    private val nameAccessor: IPropertyAccessor<MPSProject> = object : IPropertyAccessor<MPSProject> {
        public override fun get(element: MPSProject): String? {
            return element.name
        }

        public override fun set(element: MPSProject, value: String?): String? {
            throw UnsupportedOperationException("readonly")
        }
    }
    override val concept: IConcept
        get() {
            return (SConceptAdapter.wrap(CONCEPTS.`Project$An`))
        }
    override val parent: INode
        get() {
            return SRepositoryAsNode(MPSModuleRepository.getInstance())
        }
    override val reference: INodeReference
        get() {
            return NodeReference(element)
        }
    override val roleInParent: String?
        get() {
            return LINKS.`projects$NW07`.getName()
        }

    override fun getChildAccessor(role: String?): IChildAccessor<MPSProject>? {
        if ((role == LINKS.`modules$Bi3g`.getName())) {
            return modulesAccessor
        }
        if ((role == LINKS.`projectModules$VXcy`.getName())) {
            return projectModulesAccessor
        }
        return super.getChildAccessor(role)
    }

    override fun getPropertyAccessor(role: String): IPropertyAccessor<MPSProject>? {
        if (Objects.equals(role, PROPS.`name$MnvL`.getName())) {
            return nameAccessor
        }
        return super.getPropertyAccessor(role)
    }

    class NodeReference : INodeReference {
        var projectName: String?
            private set
        private var path: String?

        constructor(project: MPSProject?) {
            projectName = project!!.getName()
            path = project.getProject().getPresentableUrl()
        }

        constructor(projectName: String?, path: String?) {
            this.projectName = projectName
            this.path = path
        }

        public override fun serialize(): String {
            return "mps-project:" + projectName
        }

        public override fun resolveNode(area: IArea?): ProjectAsNode? {
            val projects: List<Project> = ProjectManager.getInstance().getOpenedProjects()
            val project: MPSProject? = ListSequence.fromList(projects).ofType(
                MPSProject::class.java,
            ).findFirst(object : IWhereFilter<MPSProject>() {
                public override fun accept(it: MPSProject): Boolean {
                    return Objects.equals(it.getName(), projectName) && Objects.equals(
                        it.getProject().getPresentableUrl(), path,
                    )
                }
            })
            return (if (project == null) null else ProjectAsNode(project))
        }

        public override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || this.javaClass != o.javaClass) {
                return false
            }
            val that: NodeReference = o as NodeReference
            if ((if (path != null) !(((path as Any) == that.path)) else that.path != null)) {
                return false
            }
            if ((if (projectName != null) !(((projectName as Any) == that.projectName)) else that.projectName != null)) {
                return false
            }
            return true
        }

        public override fun hashCode(): Int {
            var result: Int = 0
            result = 31 * result + ((if (path != null) path.toString().hashCode() else 0))
            result = 31 * result + ((if (projectName != null) projectName.toString().hashCode() else 0))
            return result
        }
    }

    private object CONCEPTS {
        /*package*/
        val `Project$An`: SConcept = MetaAdapterFactory.getConcept(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x37a0917d689de959L,
            "org.modelix.model.repositoryconcepts.structure.Project",
        )
    }

    private object LINKS {
        /*package*/
        val `projects$NW07`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x69652614fd1c516L,
            0x620a8558361d3e0cL,
            "projects",
        )

        /*package*/
        val `modules$Bi3g`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x37a0917d689de959L,
            0x37a0917d689de9e2L,
            "modules",
        )

        /*package*/
        val `projectModules$VXcy`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x37a0917d689de959L,
            0x3a4fe9e427e83268L,
            "projectModules",
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
        private val modulesAccessor: IChildAccessor<MPSProject> = object : IChildAccessor<MPSProject> {
            public override fun get(project: MPSProject): Iterable<INode> {
                return Sequence.fromIterable(emptyList())
            }
        }
        private val projectModulesAccessor: IChildAccessor<MPSProject> = object : IChildAccessor<MPSProject> {
            public override fun get(project: MPSProject): Iterable<INode> {
                val modules: Iterable<SModule> = project.getProjectModules()
                return Sequence.fromIterable(modules)
                    .select<INode>(object : ISelector<SModule, ProjectModuleAsNode>() {
                        public override fun select(it: SModule): ProjectModuleAsNode {
                            return ProjectModuleAsNode(project, it)
                        }
                    })
            }
        }
    }
}
