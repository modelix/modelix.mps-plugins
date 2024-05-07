package org.modelix.mps.sync.mps.util

import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal fun SModuleReference.getModelixId() = this.moduleId.toString()
