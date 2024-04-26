package org.modelix.mps.sync.transformation

import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
abstract class SynchronizationException(message: String, cause: Exception? = null) : Exception("Synchronization error occurred: $message", cause)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelixToMpsSynchronizationException(message: String, cause: Exception? = null) : SynchronizationException(message, cause)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class MpsToModelixSynchronizationException(message: String, cause: Exception? = null) : SynchronizationException(message, cause)
