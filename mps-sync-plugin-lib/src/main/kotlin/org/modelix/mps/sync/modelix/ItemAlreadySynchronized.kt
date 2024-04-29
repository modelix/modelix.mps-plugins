package org.modelix.mps.sync.modelix

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * A token to pass around signaling that a certain item is already synchronized to Modelix.
 *
 * E.g. Modules A and B depend on Module C and none of them are synchronized to Modelix yet. When we synchronize them
 * then Module A and B are synced in parallel, so one of them will sync Module C first and the other will see that
 * the Module is already on the server. However, in this case it is not an error, because we were expected to sync
 * Module C to the server. So in this case Module A just creates this token to let the sync flow know that Module C
 * does not have to be synced again.
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
abstract class ItemAlreadySynchronized(val item: Any)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelAlreadySynchronized(val model: SModel) : ItemAlreadySynchronized(model)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleAlreadySynchronized(val module: SModule) : ItemAlreadySynchronized(module)

/**
 * An exception to show that the referred item is already synchronized to Modelix.
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
open class ItemAlreadySynchronizedException(
    val item: Any,
    message: String = "Item $item already exists on the server. Remove it from the project and synchronize it from the server instead.",
) : Exception(message)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelAlreadySynchronizedException(val model: SModel) : ItemAlreadySynchronizedException(
    model,
    "Model '${model.name}' in Module '${model.module?.moduleName}' already exists on the server, therefore it and its parent module will not be synchronized completely. Remove the parent module from the project and synchronize it from the server instead.",
)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModuleAlreadySynchronizedException(val module: SModule) : ItemAlreadySynchronizedException(
    module,
    "Module '${module.moduleName}' already exists on the server, therefore it will not be synchronized. Remove it from the project and synchronize it from the server instead.",
)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class NodeAlreadySynchronizedException(val node: SNode) : ItemAlreadySynchronizedException(
    node,
    "Node '${node.name}' already exists on server, therefore it will not be synched. Remove its parent node or its parent model and synchronize the parent model from the server instead.",
)
