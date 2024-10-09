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

import org.modelix.kotlin.utils.UnstableModelixFeature
import java.util.concurrent.CompletableFuture

/**
 * Represents a synchronization task between MPS and the modelix model server.
 *
 * @param requiredLocks the synchronization locks we need to run the task.
 *
 * @property syncDirection the synchronization direction of the task.
 * @property action the actual code to execute when the task is running.
 * @property previousTaskResultHolder the result of the previous [SyncTask], if they are chained together. (For details
 * see [ContinuableSyncTask.continueWith]).
 * @property result the result of this synchronization task.
 *
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class SyncTask(
    requiredLocks: LinkedHashSet<SyncLock>,
    val syncDirection: SyncDirection,
    val action: SyncTaskAction,
    val previousTaskResultHolder: CompletableFuture<Any?>? = null,
    val result: CompletableFuture<Any?> = CompletableFuture(),
) {
    /**
     * The required synchronization locks in the correct order sorted by the [SyncLockComparator].
     */
    val sortedLocks = LinkedHashSet<SyncLock>(requiredLocks.sortedWith(SyncLockComparator()))

    /**
     * The stack trace for debug purposes, to know where the task was created.
     */
    private val stackTrace: List<StackTraceElement> = Thread.currentThread().stackTrace.toList()
}

/**
 * Represents a chaining of [SyncTask]s: a [SyncTask] that will be continued by another [SyncTask].
 *
 * @property previousTask the previous [SyncTask] just before this task.
 * @property syncQueue the synchronization queue in which the [SyncTask]s are running.
 * @property futuresWaitQueue a queue to help scheduling the continuation [SyncTask] if the [previousTask] has finished.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ContinuableSyncTask(
    private val previousTask: SyncTask,
    private val syncQueue: SyncQueue,
    private val futuresWaitQueue: FuturesWaitQueue,
) {

    /**
     * Schedules a [SyncTask] with the given [requiredLocks], [syncDirection] and [action] after the [previousTask].
     * The method uses the [futuresWaitQueue] to schedule the continuation task only if the [previousTask] completed
     * successfully. If the [previousTask] failed, then it will fail the continuation task also and all the other
     * tasks that are coming after that task (i.e. all the following [ContinuableSyncTask]s).
     *
     * @param requiredLocks the locks used by the [SyncTask].
     * @param syncDirection the synchronization direction of the [SyncTask].
     * @param action the action to do in the [SyncTask].
     *
     * @return a [ContinuableSyncTask] so that we can chain [SyncTask]s after each other.
     */
    fun continueWith(
        requiredLocks: LinkedHashSet<SyncLock>,
        syncDirection: SyncDirection,
        action: SyncTaskAction,
    ): ContinuableSyncTask {
        val continuation = CompletableFuture<Any?>()
        futuresWaitQueue.add(continuation, setOf(getResult()), true)

        val task = SyncTask(requiredLocks, syncDirection, action, continuation)
        continuation.whenComplete { _, throwable ->
            if (throwable != null) {
                // if a predecessor failed then we have to fail the next task
                task.result.completeExceptionally(throwable)
            } else {
                // this will only run if previousTask is completed according to the FuturesWaitQueue
                syncQueue.enqueue(task)
            }
        }

        return ContinuableSyncTask(task, syncQueue, futuresWaitQueue)
    }

    /**
     * @return the result of the [previousTask].
     */
    fun getResult() = previousTask.result
}

/**
 * A type alias placeholder for a lambda that has one parameter which can be null and has a return value that can be
 * also null.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
typealias SyncTaskAction = (Any?) -> Any?
