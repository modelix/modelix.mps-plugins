package org.modelix.mps.sync.mps.notifications

import mu.KLogger
import org.modelix.kotlin.utils.UnstableModelixFeature
import java.util.Objects
import java.util.concurrent.locks.ReentrantLock

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object InjectableNotifierWrapper {
    private val mutex = ReentrantLock()
    private var lastMessage = ""
    private var lastError: Throwable? = null

    var notifier: INotifier = NoOpNotifier()

    fun notifyAndLogError(message: String, error: Throwable, logger: KLogger) {
        runWithLock {
            if (lastMessage != message || !errorsHaveSameOrigin(lastError, error)) {
                lastMessage = message
                lastError = error

                logger.error(error) { message }
                notifier.error(message)
            }
        }
    }

    fun notifyAndLogWarning(message: String, logger: KLogger) {
        runWithLock {
            if (lastMessage != message) {
                lastMessage = message
                lastError = null

                logger.warn { message }
                notifier.warning(message)
            }
        }
    }

    fun notifyAndLogInfo(message: String, logger: KLogger) {
        runWithLock {
            if (lastMessage != message) {
                lastMessage = message
                lastError = null

                logger.info { message }
                notifier.info(message)
            }
        }
    }

    private fun runWithLock(action: () -> Unit) {
        try {
            mutex.lock()
            action()
        } finally {
            mutex.unlock()
        }
    }

    private fun errorsHaveSameOrigin(errorOne: Throwable?, errorTwo: Throwable?) =
        Objects.equals(errorOne?.cause?.stackTrace?.first(), errorTwo?.cause?.stackTrace?.first())
}
