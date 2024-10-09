/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.mps.sync.tasks

import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.mps.notifications.WrappedNotifier
import org.modelix.mps.sync.mps.services.InjectableService
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.util.completeWithDefault
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * A helper class that lets you build asynchronous fork-join like structures, where each fork (predecessor) is
 * represented by a [CompletableFuture] for which the join is waiting. The join (continuation) is also represented
 * by a [CompletableFuture]. See [run] for details about how the join is waiting for the completion of the predecessors,
 * and what happens to it depending on the result of the predecessors.
 *
 * This class is strongly related to the execution of [SyncTask] and [ContinuableSyncTask] classes, because it offers
 * an asynchronous way to wait for the results of other tasks, without blocking the current thread and holding too many
 * locks, let them be MPS read/write locks or read/write modelix transactions. Therefore, it avoids deadlocks too.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class FuturesWaitQueue : Runnable, InjectableService {

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * The queue of continuations to be processed.
     *
     * @see [FutureWithPredecessors].
     */
    private val continuations = LinkedBlockingQueue<FutureWithPredecessors>()

    /**
     * The thread pool on which this object (Runnable) is running.
     *
     * Alternatively, the class could have inherited from [Thread] and we could use its [Thread.start] and
     * [Thread.interrupt] methods to start and stop the execution.
     */
    private val threadPool = Executors.newSingleThreadExecutor()

    /**
     * An object to wait for. It is used so the [run] method does not quit, neither is the CPU "burning" in an endless
     * while(true) loop, if [continuations] is empty.
     */
    private val pauseObject = Object()

    /**
     * A notifier that can notify the user about certain messages in a nicer way than just simply logging the message.
     */
    private lateinit var notifier: WrappedNotifier

    override fun initService(serviceLocator: ServiceLocator) {
        notifier = serviceLocator.wrappedNotifier

        threadPool.submit(this)
    }

    /**
     * Adds a new [continuation] with several [predecessors] to the queue. If the [predecessors] complete (abort) then
     * the [continuation] completes (aborts, respectively).
     *
     * @param continuation the [CompletableFuture] to continue with if the predecessors finished.
     * @param predecessors the [CompletableFuture]s for which we are waiting for.
     * @param fillContinuation if true, then the continuation will be completed by the result of the [predecessors]. See
     * the [collectResults] parameter about how we compact the results if there is more than one predecessor.
     * @param collectResults if true and there is more than one predecessor, then their results will be put in a List,
     * and this List will complete the [continuation]. Otherwise, we will use the result of the first predecessor to
     * complete the [continuation].
     *
     * @see [run] for details.
     */
    fun add(
        continuation: CompletableFuture<Any?>,
        predecessors: Set<CompletableFuture<Any?>>,
        fillContinuation: Boolean = false,
        collectResults: Boolean = false,
    ) {
        if (predecessors.isEmpty()) {
            if (collectResults) {
                continuation.complete(Collections.emptyList<Any?>())
            } else {
                continuation.completeWithDefault()
            }
            return
        }

        continuations.add(
            FutureWithPredecessors(
                predecessors,
                FillableFuture(continuation, fillContinuation || collectResults, collectResults),
            ),
        )
        notifyThread()
    }

    override fun dispose() {
        threadPool.shutdownNow()
    }

    /**
     * Until interrupted, it goes through each item of the [continuations] queue. It takes the first item and checks
     * the state of the predecessor [CompletableFuture]s:
     *
     *   - if any of them failed (completed exceptionally), then it fails the continuation with the same [Throwable] as
     *   the first predecessor that failed.
     *
     *   - if any of them was cancelled, then it cancels the continuation too.
     *
     *   - if all of them completed without exception, then:
     *
     *       - it unwraps the predecessors. Those predecessors whose results is a [CompletableFuture] will be replaced
     *       by their results. Those predecessors whose results are not a [CompletableFuture] will be kept as is. This
     *       new construct will be put back to the end of the [continuations] queue with the same continuation as
     *       before. With other words: we recursively unwrap the results until we find a non-[CompletableFuture] result.
     *
     *       - if the results of all predecessors are not [CompletableFuture]s, then if we have to use the results of
     *       the predecessors ([FillableFuture.shallBeFilled] is true), then we complete the continuation as follows.
     *       If we have to collect all results of the predecessors ([FillableFuture.shallCollectResults] is true), then
     *       these results will be put in a List and this List will complete the continuation. Otherwise, we will take
     *       the result of the first predecessor and complete the continuation with this value.
     *
     *       - if we do not have to use the results of the predecessors, then we complete the continuation with null
     *
     * If the queue's executor thread gets interrupted or any Exception occurs while processing the queue, then we
     * complete all continuations exceptionally with the Throwable that occurred.
     */
    override fun run() {
        val executorThread = Thread.currentThread()
        try {
            while (!executorThread.isInterrupted) {
                while (!continuations.isEmpty()) {
                    if (executorThread.isInterrupted) {
                        throw InterruptedException()
                    }

                    val futureWithPredecessors = continuations.take()
                    val predecessors = futureWithPredecessors.predecessors

                    val fillableFuture = futureWithPredecessors.future
                    val continuation = fillableFuture.continuation

                    val failedPredecessor =
                        predecessors.firstOrNull { predecessor -> predecessor.isCompletedExceptionally }
                    if (failedPredecessor != null) {
                        failedPredecessor.handle { _, throwable -> continuation.completeExceptionally(throwable) }
                        continue
                    }

                    val anyCancelled = predecessors.any { predecessor -> predecessor.isCancelled }
                    if (anyCancelled) {
                        continuation.cancel(true)
                        continue
                    }

                    val allCompleted =
                        predecessors.all { predecessor -> predecessor.isDone && !predecessor.isCompletedExceptionally }
                    if (allCompleted) {
                        /*
                         * Check if there is any predecessor whose result (.get()) is a CompletableFuture. Replace such
                         * CompletableFutures with their result and put them at the end of the queue.
                         *
                         * In the normal case, such predecessors are created if Iterable<T>.waitForCompletionOfEach is
                         * the last statement of a SyncTask, that returns a CompletableFuture. This Future will be put
                         * inside SyncTask.result, which is a Future already. However, we are curious about the
                         * completion of the inner Future, thus we have to unpack it here.
                         *
                         * As a consequence of this feature, it is not possible to pass a CF from a SyncTask to a
                         * consecutive SyncTask via the predecessor's return and the successors input parameter, because
                         * this CF will always be unpacked until no more CFs are found.
                         */
                        @Suppress("UNCHECKED_CAST")
                        val cfPredecessors = predecessors.filter { it.get() is CompletableFuture<*> }
                            .map { it.get() as CompletableFuture<Any?> }
                        if (cfPredecessors.isNotEmpty()) {
                            val normalPredecessors = predecessors.filter { it.get() !is CompletableFuture<*> }
                            val newPredecessors = Stream.concat(normalPredecessors.stream(), cfPredecessors.stream())
                                .collect(Collectors.toSet())

                            val nextRound = FutureWithPredecessors(newPredecessors, fillableFuture)
                            continuations.add(nextRound)
                            continue
                        }

                        val fillContinuation = fillableFuture.shallBeFilled
                        val result = if (fillContinuation) {
                            if (fillableFuture.shallCollectResults) {
                                try {
                                    predecessors.map { it.get() }
                                } catch (ex: Exception) {
                                    logger.error(ex) { "Error while collecting results from predecessors. Failing continuation." }
                                    continuation.completeExceptionally(ex)
                                    continue
                                }
                            } else {
                                predecessors.first().get()
                            }
                        } else {
                            null
                        }
                        continuation.complete(result)

                        continue
                    }

                    // re-queue item if it was not processed
                    continuations.add(futureWithPredecessors)
                }

                waitForNotification()
            }
        } catch (t: Throwable) {
            if (!threadPool.isShutdown) {
                val message = "${javaClass.simpleName} is shutting down, because of an Exception. Cause: ${t.message}"
                notifier.notifyAndLogError(message, t, logger)
            }
            continuations.forEach { it.future.continuation.completeExceptionally(t) }
        }
    }

    /**
     * Notifies the thread if it is waiting for the [pauseObject], so that the [continuations] can be processed.
     */
    private fun notifyThread() {
        synchronized(pauseObject) {
            pauseObject.notifyAll()
        }
    }

    /**
     * Enters the monitor of [pauseObject] to wait for a [notifyThread] call.
     */
    private fun waitForNotification() {
        synchronized(pauseObject) {
            pauseObject.wait()
        }
    }
}

/**
 * A data class to keep the [predecessors] ("forks") and the continuation ("join") of a computation chain together.
 *
 * @param predecessors the previous computations for whose results we are waiting for.
 * @param future the continuation computation we want to do after the predecessors finished.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class FutureWithPredecessors(val predecessors: Set<CompletableFuture<Any?>>, val future: FillableFuture)

/**
 * The [continuation] with some control parameters.
 *
 * @param continuation the continuation computation we want to do.
 * @param shallBeFilled if true, then the results of the predecessors will complete the [continuation].
 * @param shallCollectResults if true, then the results of the predecessors will be put in a List, otherwise we will
 * use the result of the first predecessor to complete the [continuation].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class FillableFuture(
    val continuation: CompletableFuture<Any?>,
    val shallBeFilled: Boolean = false,
    val shallCollectResults: Boolean = false,
)
