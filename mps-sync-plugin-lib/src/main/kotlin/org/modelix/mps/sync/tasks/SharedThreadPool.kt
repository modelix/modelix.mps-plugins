package org.modelix.mps.sync.tasks

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import org.modelix.kotlin.utils.UnstableModelixFeature
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * An application-level shared fixed size thread pool backed by available CPU number of threads. It can be used to
 * run [Runnable]s in the background without having to take care of the executor [Thread]'s lifecycle.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Service(Service.Level.APP)
class SharedThreadPool : Disposable {

    /**
     * The thread pool on which we run the [Runnable]s.
     */
    private val threadPool: ExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    /**
     * @return true if the [threadPool] is shut down.
     *
     * @see [ExecutorService.isShutdown].
     */
    val isShutdown: Boolean
        get() = threadPool.isShutdown

    /**
     * Submit a [Runnable] to the [threadPool] for execution.
     *
     * @param task the [Runnable] to run in the [threadPool].
     *
     * @return a [Future] that can be used to track the lifecycle of the [Runnable].
     *
     * @see [ExecutorService.submit].
     */
    fun submit(task: Runnable): Future<*> = threadPool.submit(task)

    override fun dispose() {
        threadPool.shutdownNow()
    }
}
