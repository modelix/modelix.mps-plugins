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
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors
import java.util.stream.Stream

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class FuturesWaitQueue : Runnable, InjectableService {

    private val logger = KotlinLogging.logger {}

    private val continuations = LinkedBlockingQueue<FutureWithPredecessors>()
    private val threadPool = Executors.newSingleThreadExecutor()
    private val pauseObject = Object()

    private lateinit var notifier: WrappedNotifier

    override fun initService(serviceLocator: ServiceLocator) {
        notifier = serviceLocator.wrappedNotifier

        threadPool.submit(this)
    }

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
                continuation.complete(null)
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
                    val continuation = fillableFuture.future

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
            continuations.forEach { it.future.future.completeExceptionally(t) }
        }
    }

    private fun notifyThread() {
        synchronized(pauseObject) {
            pauseObject.notifyAll()
        }
    }

    private fun waitForNotification() {
        synchronized(pauseObject) {
            pauseObject.wait()
        }
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class FutureWithPredecessors(val predecessors: Set<CompletableFuture<Any?>>, val future: FillableFuture)

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class FillableFuture(
    val future: CompletableFuture<Any?>,
    val shallBeFilled: Boolean = false,
    val shallCollectResults: Boolean = false,
)
