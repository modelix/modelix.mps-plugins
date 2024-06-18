package org.modelix.mps.sync.transformation.exceptions

import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
abstract class SynchronizationException(override val message: String, cause: Throwable? = null) :
    Exception(message, cause)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
class ModelixToMpsSynchronizationException(message: String, cause: Throwable? = null) :
    SynchronizationException("Error occurred, while synchronizing from the server: $message", cause)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
class MpsToModelixSynchronizationException(message: String, cause: Throwable? = null) :
    SynchronizationException("Error occurred, while synchronizing to the server: $message", cause)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
const val pleaseCheckLogs = "The error provided no further information, please check logs for details."
