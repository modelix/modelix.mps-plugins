package org.modelix.mps.sync.transformation.exceptions

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * A token to pass around signaling that a certain [item] is already synchronized to modelix.
 *
 * E.g. Modules A and B depend on Module C and none of them are synchronized to modelix yet. When we synchronize them
 * then Module A and B are synced in parallel, so one of them will sync Module C first and the other will see that
 * the Module is already on the server. However, in this case it is not an error, because we were expected to sync
 * Module C to the server. So in this case Module A just creates this token to let the sync flow know that Module C
 * does not have to be synced again.
 *
 * @property item the object we want to put into the token.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
abstract class ItemAlreadySynchronized(open val item: Any)

/**
 * A token to pass around signaling that [item] is already synchronized to modelix.
 *
 * @property item the MPS Model.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelAlreadySynchronized(override val item: SModel) : ItemAlreadySynchronized(item)

/**
 * A token to pass around signaling that [item] is already synchronized to modelix.
 *
 * @property item the MPS Module.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModuleAlreadySynchronized(override val item: SModule) : ItemAlreadySynchronized(item)

/**
 * An exception to show that the referred [item] is already synchronized to modelix.
 *
 * @property item the object that is already synchronized to modelix.
 * @property message an error message
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
abstract class ItemAlreadySynchronizedException(open val item: Any, message: String) : Exception(message)

/**
 * An exception to show that the referred [item] is already synchronized to modelix.
 *
 * @property item the MPS Model.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelAlreadySynchronizedException(override val item: SModel) : ItemAlreadySynchronizedException(
    item,
    "Model '${item.name}' in Module '${item.module?.moduleName}' already exists on the server, therefore it and its parent module will not be synchronized completely. Remove the parent module from the project and synchronize it from the server instead.",
)

/**
 * An exception to show that the referred [item] is already synchronized to modelix.
 *
 * @property item the MPS Module.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModuleAlreadySynchronizedException(override val item: SModule) : ItemAlreadySynchronizedException(
    item,
    "Module '${item.moduleName}' already exists on the server, therefore it will not be synchronized. Remove it from the project and synchronize it from the server instead.",
)

/**
 * An exception to show that the referred [item] is already synchronized to modelix.
 *
 * @property item the MPS Node.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class NodeAlreadySynchronizedException(override val item: SNode) : ItemAlreadySynchronizedException(
    item,
    "Node '${item.name}' already exists on server, therefore it will not be synched. Remove its parent node or its parent model and synchronize the parent model from the server instead.",
)
