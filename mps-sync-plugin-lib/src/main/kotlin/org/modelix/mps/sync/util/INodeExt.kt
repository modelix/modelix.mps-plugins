/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.mps.sync.util

import jetbrains.mps.smodel.SNodeId.Regular
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.mpsadapters.MPSNode

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
fun INode.nodeIdAsLong(): Long =
    when (this) {
        is PNodeAdapter -> this.nodeId
        is MPSNode -> {
            val nodeId = this.node.nodeId
            check(nodeId is Regular) { "Unsupported NodeId format: $nodeId" }
            nodeId.id
        }

        else -> throw IllegalStateException("Unsupported INode implementation")
    }

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
fun INode.isModule(): Boolean {
    val concept = this.concept ?: return false
    return concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.Module)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
fun INode.isModel(): Boolean {
    val concept = this.concept ?: return false
    return concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.Model)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
fun INode.isDevKitDependency(): Boolean {
    val concept = this.concept ?: return false
    return concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
fun INode.isSingleLanguageDependency(): Boolean {
    val concept = this.concept ?: return false
    return concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
fun INode.isModelImport(): Boolean {
    val concept = this.concept ?: return false
    val isModelReference = concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.ModelReference)
    val isModelImportRole = BuiltinLanguages.MPSRepositoryConcepts.Model.modelImports == this.getContainmentLink()
    return isModelReference && isModelImportRole
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
fun INode.isModuleDependency(): Boolean {
    val concept = this.concept ?: return false
    return concept.isSubConceptOf(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
fun INode.getModel(): INode? = findNode { it.isModel() }

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
fun INode.getModule(): INode? = findNode { it.isModule() }

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
fun INode.getMpsNodeId(): SNodeId {
    val mpsNodeIdAsString = getOriginalReference()
    val mpsId = mpsNodeIdAsString?.let { PersistenceFacade.getInstance().createNodeId(it) }
    return if (mpsId != null) {
        mpsId
    } else {
        val id = nodeIdAsLong().toString().let {
            val foreignPrefix = jetbrains.mps.smodel.SNodeId.Foreign.ID_PREFIX
            if (!it.startsWith(foreignPrefix)) {
                "$foreignPrefix$it"
            } else {
                it
            }
        }
        jetbrains.mps.smodel.SNodeId.Foreign(id)
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
private fun INode.findNode(criterion: (INode) -> Boolean): INode? {
    if (criterion(this)) {
        return this
    }

    var node = this.parent
    while (node != null) {
        if (criterion(node)) {
            return node
        }
        node = node.parent
    }

    return null
}
