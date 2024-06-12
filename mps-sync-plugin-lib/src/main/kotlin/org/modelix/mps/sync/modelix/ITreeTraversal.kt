package org.modelix.mps.sync.modelix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.getRootNode
import org.modelix.mps.sync.util.isDevKitDependency
import org.modelix.mps.sync.util.isModel
import org.modelix.mps.sync.util.isModule
import org.modelix.mps.sync.util.isSingleLanguageDependency
import org.modelix.mps.sync.util.nodeIdAsLong

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal fun IBranch.visit(visitor: ITreeVisitor, coroutineScope: CoroutineScope) =
    runRead { runBlocking(coroutineScope.coroutineContext) { getRootNode().visit(visitor) } }

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal suspend fun INode.visit(visitor: ITreeVisitor) {
    val visitedNode = this

    if (isModule()) {
        visitor.visitModule(this)

        val dependenciesJobs = coroutineScope {
            getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies).map {
                launch {
                    visitor.visitModuleDependency(visitedNode, it)
                }
            }
        }
        dependenciesJobs.joinAll()

        val modelsJobs = coroutineScope {
            getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).map {
                launch {
                    it.visit(visitor)
                }
            }
        }
        modelsJobs.joinAll()
    } else if (isModel()) {
        visitor.visitModel(this)

        val modelImportJobs = coroutineScope {
            getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports).map {
                launch {
                    visitor.visitModelImport(visitedNode, it)
                }
            }
        }
        modelImportJobs.joinAll()

        val usedLanguagesJobs = coroutineScope {
            getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages).map {
                launch {
                    if (it.isDevKitDependency()) {
                        // visit devkit dependency
                        visitor.visitDevKitDependency(visitedNode, it)
                    } else if (it.isSingleLanguageDependency()) {
                        // visit language dependency
                        visitor.visitLanguageDependency(visitedNode, it)
                    } else {
                        val nodeId = visitedNode.nodeIdAsLong()
                        val message =
                            "Node ($nodeId) is not transformed, because it is neither DevKit nor SingleLanguageDependency."
                        throw IllegalStateException(message)
                    }
                }
            }
        }
        usedLanguagesJobs.joinAll()

        val rootNodesJobs = coroutineScope {
            getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).map {
                launch {
                    it.visit(visitor)
                }
            }
        }
        rootNodesJobs.joinAll()
    } else {
        visitor.visitNode(this)

        val childrenJobs = coroutineScope {
            allChildren.map {
                launch {
                    it.visit(visitor)
                }
            }
        }
        childrenJobs.joinAll()
    }
}

/**
 * The visited nodes are always in a read transaction, so we can read any data from modelix.
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
