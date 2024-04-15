package org.modelix.mps.sync.modelix

import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * A token to pass around signaling that a certain item is already synchronized to Modelix.
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ItemAlreadySynchronizer(val item: Any)
