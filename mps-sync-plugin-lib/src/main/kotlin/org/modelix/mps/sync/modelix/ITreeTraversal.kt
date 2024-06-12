package org.modelix.mps.sync.modelix

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.getRootNode
import org.modelix.mps.sync.modelix.util.isDevKitDependency
import org.modelix.mps.sync.modelix.util.isModel
import org.modelix.mps.sync.modelix.util.isModule
import org.modelix.mps.sync.modelix.util.isSingleLanguageDependency
import org.modelix.mps.sync.modelix.util.nodeIdAsLong

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ITreeTraversal(val branch: IBranch) {

    suspend fun visit(visitor: ITreeVisitor) {
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

    private suspend fun visit(node: INode, visitor: ITreeVisitor) {
        if (node.isModule()) {
            visitor.visitModule(node)

            val dependenciesJobs = mutableListOf<Job>()
            coroutineScope {
                branch.runRead {
                    dependenciesJobs.addAll(
                        node.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies).map {
                            launch {
                                visitor.visitModuleDependency(node, it)
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
        } else if (node.isModel()) {
            visitor.visitModel(node)

            val modelImportJobs = mutableListOf<Job>()
            coroutineScope {
                branch.runRead {
                    modelImportJobs.addAll(
                        node.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports).map {
                            launch {
                                visitor.visitModelImport(node, it)
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
                            launch {
                                if (it.isDevKitDependency()) {
                                    // visit devkit dependency
                                    visitor.visitDevKitDependency(node, it)
                                } else if (it.isSingleLanguageDependency()) {
                                    // visit language dependency
                                    visitor.visitLanguageDependency(node, it)
                                } else {
                                    val nodeId = node.nodeIdAsLong()
                                    val message =
                                        "Node ($nodeId) is not transformed, because it is neither DevKit nor SingleLanguageDependency."
                                    throw IllegalStateException(message)
                                }
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
 * The visited nodes are always in a read transaction, so we can read any data from modelix.
 * TODO document the interface and its methods...
 */
interface ITreeVisitor {
    suspend fun visitModule(node: INode)
    suspend fun visitModel(node: INode)
    suspend fun visitNode(node: INode)
    suspend fun visitModuleDependency(sourceModule: INode, moduleDependency: INode)
    suspend fun visitDevKitDependency(sourceModel: INode, devKitDependency: INode)
    suspend fun visitLanguageDependency(sourceModel: INode, languageDependency: INode)
    suspend fun visitModelImport(sourceModel: INode, modelImport: INode)
}
