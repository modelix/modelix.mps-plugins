package org.modelix.mps.sync.modelix

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * A token to pass around signaling that a certain item is already synchronized to Modelix.
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
open class ItemAlreadySynchronizer(val item: Any)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelAlreadySynchronized(val model: SModel) : ItemAlreadySynchronizer(model)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleAlreadySynchronized(val module: SModule) : ItemAlreadySynchronizer(module)
