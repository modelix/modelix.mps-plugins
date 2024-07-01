package org.modelix.mps.sync.modelix.tree

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.getRootNode
import org.modelix.mps.sync.modelix.ModelixSyncPluginConcepts
import org.modelix.mps.sync.modelix.util.isDevKitDependency
import org.modelix.mps.sync.modelix.util.isModel
import org.modelix.mps.sync.modelix.util.isModule
import org.modelix.mps.sync.modelix.util.isReadonlyModel
import org.modelix.mps.sync.modelix.util.isReadonlyModelImport
import org.modelix.mps.sync.modelix.util.isReadonlyModule
import org.modelix.mps.sync.modelix.util.isReadonlyModuleDependency
import org.modelix.mps.sync.modelix.util.isSingleLanguageDependency
import org.modelix.mps.sync.modelix.util.nodeIdAsLong

/**
 * Traverses a branch starting from the [IBranch.getRootNode]'s children until the last node in the branch hierarchy.
 *
 * @property branch the [IBranch] whose content will be traversed
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ITreeTraversal(val branch: IBranch) {

    /**
     * Calls the [visitor] to visit each node in the [branch] starting from the [IBranch.getRootNode]'s children until
     * the last node in the branch.
     *
     * @param visitor the visitor that visits each node in the branch, starting from the root node's children
     */
    suspend fun visit(visitor: IBranchVisitor) {
        val childrenJobs = mutableListOf<Job>()
        coroutineScope {
            branch.runRead {
                childrenJobs.addAll(
                    branch.getRootNode().allChildren.map {
                        launch {
                            visit(it, visitor)
                        }
                    },
                )
            }
        }
        childrenJobs.joinAll()
    }

    private suspend fun visit(node: INode, visitor: IBranchVisitor) {
        if (node.isReadonlyModule()) {
            visitor.visitReadonlyModule(node)
            val modelsJobs = mutableListOf<Job>()
            coroutineScope {
                branch.runRead {
                    modelsJobs.addAll(
                        node.getChildren(ModelixSyncPluginConcepts.ReadonlyModule.readonlyModels).map {
                            launch {
                                visit(it, visitor)
                            }
                        },
                    )
                }
            }
            modelsJobs.joinAll()
        } else if (node.isModule()) {
            visitor.visitModule(node)

            val dependenciesJobs = mutableListOf<Job>()
            coroutineScope {
                branch.runRead {
                    dependenciesJobs.addAll(
                        node.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies).map {
                            if (it.isReadonlyModuleDependency()) {
                                launch {
                                    visitor.visitReadonlyModuleDependency(node, it)
                                }
                            } else {
                                launch {
                                    visitor.visitModuleDependency(node, it)
                                }
                            }
                        },
                    )
                }
            }
            dependenciesJobs.joinAll()

            val modelsJobs = mutableListOf<Job>()
            coroutineScope {
                branch.runRead {
                    modelsJobs.addAll(
                        node.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).map {
                            launch {
                                visit(it, visitor)
                            }
                        },
                    )
                }
            }
            modelsJobs.joinAll()
        } else if (node.isReadonlyModel()) {
            visitor.visitReadonlyModel(node)

            val rootNodesJobs = mutableListOf<Job>()
            coroutineScope {
                branch.runRead {
                    rootNodesJobs.addAll(
                        node.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).map {
                            launch {
                                visitor.visitReadonlyModelNode(it)
                            }
                        },
                    )
                }
            }
            rootNodesJobs.joinAll()
        } else if (node.isModel()) {
            visitor.visitModel(node)

            val modelImportJobs = mutableListOf<Job>()
            coroutineScope {
                branch.runRead {
                    modelImportJobs.addAll(
                        node.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports).map {
                            if (it.isReadonlyModelImport()) {
                                launch {
                                    visitor.visitReadonlyModelImport(node, it)
                                }
                            } else {
                                launch {
                                    visitor.visitModelImport(node, it)
                                }
                            }
                        },
                    )
                }
            }
            modelImportJobs.joinAll()

            val usedLanguagesJobs = mutableListOf<Job>()
            coroutineScope {
                branch.runRead {
                    usedLanguagesJobs.addAll(
                        node.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages).map {
                            if (it.isDevKitDependency()) {
                                launch {
                                    visitor.visitDevKitDependency(node, it)
                                }
                            } else if (it.isSingleLanguageDependency()) {
                                launch {
                                    visitor.visitLanguageDependency(node, it)
                                }
                            } else {
                                val nodeId = node.nodeIdAsLong()
                                val message =
                                    "Node ($nodeId) is not transformed, because it is neither DevKit nor SingleLanguageDependency."
                                throw IllegalStateException(message)
                            }
                        },
                    )
                }
            }
            usedLanguagesJobs.joinAll()

            val rootNodesJobs = mutableListOf<Job>()
            coroutineScope {
                branch.runRead {
                    rootNodesJobs.addAll(
                        node.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).map {
                            launch {
                                visit(it, visitor)
                            }
                        },
                    )
                }
            }
            rootNodesJobs.joinAll()
        } else {
            visitor.visitNode(node)

            val childrenJobs = mutableListOf<Job>()
            coroutineScope {
                branch.runRead {
                    childrenJobs.addAll(
                        node.allChildren.map {
                            launch {
                                visit(it, visitor)
                            }
                        },
                    )
                }
            }
            childrenJobs.joinAll()
        }
    }
}

/**
 * A visitor that can visit different kinds of [INode]s in an [IBranch].
 * */
interface IBranchVisitor {
    /**
     * Visits a [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Module] node.
     *
     * @param node the node to visit
     */
    suspend fun visitModule(node: INode)

    /**
     * Visits a [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Model] node.
     *
     * @param node the node to visit
     */
    suspend fun visitModel(node: INode)

    /**
     * Visits a general node.
     *
     * @param node the node to visit
     */
    suspend fun visitNode(node: INode)

    /**
     * Visits a [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency] node.
     *
     * @param sourceModule the source [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Module] node from
     * which the [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency] originates
     *
     * @param moduleDependency the node to visit
     */
    suspend fun visitModuleDependency(sourceModule: INode, moduleDependency: INode)

    /**
     * Visits a [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency] node.
     *
     * @param sourceModel the source [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Model] node from
     * which the [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency] originates
     *
     * @param devKitDependency the node to visit
     */
    suspend fun visitDevKitDependency(sourceModel: INode, devKitDependency: INode)

    /**
     * Visits a [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency] node.
     *
     * @param sourceModel the source [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Model] node from
     * which the [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency] originates
     *
     * @param languageDependency the node to visit
     */
    suspend fun visitLanguageDependency(sourceModel: INode, languageDependency: INode)

    /**
     * Visits a [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.ModelReference] node.
     *
     * @param sourceModel the source [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Model] node from
     * which the [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.ModelReference] originates
     *
     * @param modelImport the node to visit
     */
    suspend fun visitModelImport(sourceModel: INode, modelImport: INode)

    /**
     * Visits a [org.modelix.mps.sync.modelix.ModelixSyncPluginConcepts.ReadonlyModule] node.
     *
     * @param node the node to visit
     */
    suspend fun visitReadonlyModule(node: INode)

    /**
     * Visits a [org.modelix.mps.sync.modelix.ModelixSyncPluginConcepts.ReadonlyModel] node.
     *
     * @param node the node to visit
     */
    suspend fun visitReadonlyModel(node: INode)

    /**
     * Visits a [org.modelix.mps.sync.modelix.ModelixSyncPluginConcepts.ReadonlyModelNode] node.
     *
     * @param node the node to visit
     */
    suspend fun visitReadonlyModelNode(node: INode)

    /**
     * Visits a [org.modelix.mps.sync.modelix.ModelixSyncPluginConcepts.ReadonlyModuleDependency] node.
     *
     * @param sourceModule the source [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Module] node from
     * which the [org.modelix.mps.sync.modelix.ModelixSyncPluginConcepts.ReadonlyModuleDependency] originates
     *
     * @param readonlyModuleDependency the node to visit
     */
    suspend fun visitReadonlyModuleDependency(sourceModule: INode, readonlyModuleDependency: INode)

    /**
     * Visits a [org.modelix.mps.sync.modelix.ModelixSyncPluginConcepts.ReadonlyModelReference] node.
     *
     * @param sourceModel the source [org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Model] node from
     * which the [org.modelix.mps.sync.modelix.ModelixSyncPluginConcepts.ReadonlyModelReference] originates
     *
     * @param readonlyModelImport the node to visit
     */
    suspend fun visitReadonlyModelImport(sourceModel: INode, readonlyModelImport: INode)
}
