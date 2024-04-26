package org.modelix.mps.sync.mps.notifications

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KLogger
import org.modelix.kotlin.utils.UnstableModelixFeature
import java.util.Objects

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object InjectableNotifierWrapper {
    private val mutex = Mutex()
    private var lastMessage = ""
    private var lastError: Throwable? = null

    var notifier: INotifier = NoOpNotifier()

    fun notifyAndLogError(message: String, error: Throwable, logger: KLogger) {
        runWithMutex {
            if (lastMessage != message || !errorsHaveSameOrigin(lastError, error)) {
                lastMessage = message
                lastError = error

                logger.error(error) { message }
                notifier.error(message)
            }
        }
    }

    fun notifyAndLogWarning(message: String, logger: KLogger) {
        runWithMutex {
            if (lastMessage != message) {
                lastMessage = message
                lastError = null

                logger.warn { message }
                notifier.warning(message)
            }
        }
    }

    fun notifyAndLogInfo(message: String, logger: KLogger) {
        runWithMutex {
            if (lastMessage != message) {
                lastMessage = message
                lastError = null

                logger.info { message }
                notifier.info(message)
            }
        }
    }

    private fun runWithMutex(action: () -> Unit) {
        runBlocking {
            mutex.withLock {
                action()
            }
        }
    }

    private fun errorsHaveSameOrigin(errorOne: Throwable?, errorTwo: Throwable?) =
        Objects.equals(errorOne?.cause?.stackTrace?.first(), errorTwo?.cause?.stackTrace?.first())
}
