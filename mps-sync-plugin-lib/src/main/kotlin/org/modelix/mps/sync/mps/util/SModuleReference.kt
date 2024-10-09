package org.modelix.mps.sync.mps.util

import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * @return the [SModuleReference.getModuleId] as String.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal fun SModuleReference.getModelixId() = this.moduleId.toString()
