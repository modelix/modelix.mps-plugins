package org.modelix.mps.sync.mps.util

import org.jetbrains.mps.openapi.model.SNode
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * @return [SNode.getNodeId] as String.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal fun SNode.getModelixId() = this.nodeId.toString()
