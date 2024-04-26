package org.modelix.mps.sync.transformation

import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
abstract class SynchronizationException(override val message: String? = npePleaseCheckLogs, cause: Throwable? = null) :
    Exception(message, cause)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelixToMpsSynchronizationException(message: String? = npePleaseCheckLogs, cause: Throwable? = null) :
    SynchronizationException("Error occurred, while synchronizing from the server: $message", cause)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class MpsToModelixSynchronizationException(message: String? = npePleaseCheckLogs, cause: Throwable? = null) :
    SynchronizationException("Error occurred, while synchronizing to the server: $message", cause)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
const val npePleaseCheckLogs = "NullPointerException, please check logs for details."
