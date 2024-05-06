package org.modelix.mps.sync.mps.util

import org.jetbrains.mps.openapi.model.SModelReference
import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal fun SModelReference.clone() =
    jetbrains.mps.smodel.SModelReference(this.moduleReference?.clone(), this.modelId, this.modelName)
