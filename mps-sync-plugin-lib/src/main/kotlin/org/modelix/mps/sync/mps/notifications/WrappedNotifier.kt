package org.modelix.mps.sync.mps.notifications

import mu.KLogger
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.mps.services.InjectableService

/**
 * An [InjectableService] that wraps an [INotifier] so it becomes injectable as a common Service.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class WrappedNotifier : InjectableService {

    /**
     * A notifier that can notify the user about certain messages in a nicer way than just simply logging the message.
     */
    private var notifier: INotifier = DefaultLoggerNotifier()

    /**
     * Set the notifier field.
     *
     * @param notifier the notifier to be used.
     */
    fun setNotifier(notifier: INotifier) {
        this.notifier = notifier
    }

    /**
     * First, it logs the message and the error via the parameter logger, then it notifies the [notifier] with the
     * message as an error.
     *
     * @param message the text to show to the user.
     * @param error the cause of the message.
     * @param logger to log the error and the message.
     */
    fun notifyAndLogError(message: String, error: Throwable, logger: KLogger) {
        logger.error(error) { message }
        notifier.error(message)
    }

    /**
     * First, it logs the message via the parameter logger, then it notifies the [notifier] with the message as a
     * warning.
     *
     * @param message the text to show to the user.
     * @param logger to log the message as a warning.
     */
    fun notifyAndLogWarning(message: String, logger: KLogger) {
        logger.warn { message }
        notifier.warning(message)
    }

    /**
     * First, it logs the message via the parameter logger, then it notifies the [notifier] with the message as an info.
     *
     * @param message the text to show to the user.
     * @param logger to log the message as an info.
     */
    fun notifyAndLogInfo(message: String, logger: KLogger) {
        logger.info { message }
        notifier.info(message)
    }
}
