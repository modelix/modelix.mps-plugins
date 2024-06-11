package org.modelix.mps.sync.transformation.modelixToMps.initial

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
internal fun IBranch.visit(visitor: Visitor) = this.getRootNode().visit(visitor)

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal fun INode.visit(visitor: Visitor) {
    if (this.isModule()) {
        // visit module
        visitor.visitModule(this)

        // visit module dependencies
        this.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.dependencies).forEach {
            visitor.visitModuleDependency(this, it)
        }

        // visit models inside the module
        this.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Module.models).forEach {
            it.visit(visitor)
        }
    } else if (this.isModel()) {
        // visit model
        visitor.visitModel(this)

        // visit model imports
        this.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports).forEach {
            it.visit(visitor)
        }

        // visit language and devkit dependencies
        this.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.usedLanguages).forEach { usedLanguage ->
            if (usedLanguage.isDevKitDependency()) {
                // visit devkit dependency
                visitor.visitDevKitDependency(this, usedLanguage)
            } else if (usedLanguage.isSingleLanguageDependency()) {
                // visit language dependency
                visitor.visitLanguageDependency(this, usedLanguage)
            } else {
                val nodeId = this.nodeIdAsLong()
                val message =
                    "Node ($nodeId) is not transformed, because it is neither DevKit nor SingleLanguageDependency."
                throw IllegalStateException(message)
            }
        }

        // visit root nodes
        this.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Model.rootNodes).forEach {
            it.visit(visitor)
        }
    } else {
        // visit node
        visitor.visitNode(this)

        // visit nodes's children
        this.allChildren.forEach {
            it.visit(visitor)
        }
    }
}

interface Visitor {
    fun visitModule(node: INode)
    fun visitModel(node: INode)
    fun visitNode(node: INode)
    fun visitModuleDependency(sourceModule: INode, moduleDependency: INode)
    fun visitDevKitDependency(sourceModel: INode, devKitDependency: INode)
    fun visitLanguageDependency(sourceModel: INode, languageDependency: INode)
    fun visitModelImport(sourceModel: INode, modelImport: INode)
}
