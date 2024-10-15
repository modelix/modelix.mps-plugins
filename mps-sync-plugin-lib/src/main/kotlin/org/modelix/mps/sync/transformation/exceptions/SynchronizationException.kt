package org.modelix.mps.sync.transformation.exceptions

import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * Represents a synchronization error between modelix model server and MPS.
 *
 * @property message the error message.
 * @property cause the cause of the error.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
abstract class SynchronizationException(override val message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Represents a synchronization error between modelix model server and MPS (model server -> MPS).
 *
 * @param message the error message.
 * @param cause the cause of the error.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelixToMpsSynchronizationException(message: String, cause: Throwable? = null) :
    SynchronizationException("Error occurred, while synchronizing from the server: $message", cause)

/**
 * Represents a synchronization error between MPS and modelix model server (MPS -> model server).
 *
 * @param message the error message.
 * @param cause the cause of the error.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class MpsToModelixSynchronizationException(message: String, cause: Throwable? = null) :
    SynchronizationException("Error occurred, while synchronizing to the server: $message", cause)

/**
 * A generic error message to check the logs for more details.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
const val pleaseCheckLogs = "The error provided no further information, please check logs for details."
