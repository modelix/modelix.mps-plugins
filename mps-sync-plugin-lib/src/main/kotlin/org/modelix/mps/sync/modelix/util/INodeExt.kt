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

package org.modelix.mps.sync.modelix.util

import jetbrains.mps.smodel.SNodeId.Regular
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.DevkitDependency
import org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Model
import org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.ModelReference
import org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Module
import org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency
import org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.SingleLanguageDependency
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.isSubConceptOf
import org.modelix.model.mpsadapters.MPSNode

/**
 * @return the ID of the [INode] as a [Long] value.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
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

/**
 * @return true if the node's concept is [Module].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
fun INode.isModule(): Boolean {
    val concept = this.concept ?: return false
    val moduleConceptRef = Module.getReference()
    return concept.isSubConceptOf(moduleConceptRef)
}

/**
 * @return true if the node's concept is [Model].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
fun INode.isModel(): Boolean {
    val concept = this.concept ?: return false
    val modelConceptRef = Model.getReference()
    return concept.isSubConceptOf(modelConceptRef)
}

/**
 * @return true if the node's concept is [DevkitDependency].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
fun INode.isDevKitDependency(): Boolean {
    val concept = this.concept ?: return false
    val devKitDepConceptRef = DevkitDependency.getReference()
    return concept.isSubConceptOf(devKitDepConceptRef)
}

/**
 * @return true if the node's concept is [SingleLanguageDependency].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
fun INode.isSingleLanguageDependency(): Boolean {
    val concept = this.concept ?: return false
    val languageDepConceptRef = SingleLanguageDependency.getReference()
    return concept.isSubConceptOf(languageDepConceptRef)
}

/**
 * @return true if the node's concept is [ModelReference] and the node's containment link to its parent is
 * [Model.modelImports].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
fun INode.isModelImport(): Boolean {
    val concept = this.concept ?: return false
    val modelReferenceConceptRef = ModelReference.getReference()
    val isModelReference = concept.isSubConceptOf(modelReferenceConceptRef)
    val isModelImportRole = Model.modelImports == this.getContainmentLink()
    return isModelReference && isModelImportRole
}

/**
 * @return true if the node's concept is [ModuleDependency].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
fun INode.isModuleDependency(): Boolean {
    val concept = this.concept ?: return false
    val moduleDepConceptRef = ModuleDependency.getReference()
    return concept.isSubConceptOf(moduleDepConceptRef)
}

/**
 * @return itself if the node is a [BuiltinLanguages.MPSRepositoryConcepts.Model]. Otherwise, it goes up in the
 * containment hierarchy until the Model is found. If no Model is found then it returns null.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
fun INode.getModel(): INode? = findNode { it.isModel() }

/**
 * @return itself if the node is a [Module]. Otherwise, it goes up in the containment hierarchy until the Module is
 * found. If no Module is found then it returns null.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
fun INode.getModule(): INode? = findNode { it.isModule() }

/**
 * @return the MPS [SNodeId] of the node based on the [INode.getOriginalReference] property of the node.
 *
 * @see [INode.getOriginalReference]
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
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

/**
 * Finds the first [INode] in the containment hierarchy starting from "this" node, for which the parameter [criterion]
 * is true.
 *
 * @param criterion the criterion to test on the node.
 *
 * @return itself if [criterion] is true for the node. Otherwise, it checks the [criterion] on the parent node
 * recursively until it becomes true or no more parent node exists. If there is no more parent node, then it returns
 * null.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
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
