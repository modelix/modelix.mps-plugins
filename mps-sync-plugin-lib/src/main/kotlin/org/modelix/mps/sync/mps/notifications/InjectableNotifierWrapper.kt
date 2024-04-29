package org.modelix.mps.sync.mps.notifications

import mu.KLogger
import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object InjectableNotifierWrapper {
    var notifier: INotifier = NoOpNotifier()

    fun notifyAndLogError(message: String, error: Throwable, logger: KLogger) {
        logger.error(error) { message }
        notifier.error(message)
    }

    fun notifyAndLogWarning(message: String, logger: KLogger) {
        logger.warn { message }
        notifier.warning(message)
    }

    fun notifyAndLogInfo(message: String, logger: KLogger) {
        logger.info { message }
        notifier.info(message)
    }
}
