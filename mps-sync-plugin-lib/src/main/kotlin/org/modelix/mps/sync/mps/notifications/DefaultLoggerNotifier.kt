package org.modelix.mps.sync.mps.notifications

import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class DefaultLoggerNotifier : INotifier {
    val logger = KotlinLogging.logger {}

    override fun error(message: String, responseListener: UserResponseListener?) {
        logger.error { message }
    }

    override fun warning(message: String, responseListener: UserResponseListener?) {
        logger.warn { message }
    }

    override fun info(message: String, responseListener: UserResponseListener?) {
        logger.info { message }
    }
}
