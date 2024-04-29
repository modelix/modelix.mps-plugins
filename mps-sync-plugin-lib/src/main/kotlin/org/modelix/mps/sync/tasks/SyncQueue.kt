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
import org.modelix.mps.sync.modelix.BranchRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.notifications.InjectableNotifierWrapper
import org.modelix.mps.sync.transformation.ModelixToMpsSynchronizationException
import org.modelix.mps.sync.transformation.MpsToModelixSynchronizationException
import org.modelix.mps.sync.transformation.SynchronizationException
import org.modelix.mps.sync.transformation.pleaseCheckLogs
import org.modelix.mps.sync.util.completeWithDefault
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object SyncQueue : AutoCloseable {

    private val logger = KotlinLogging.logger {}
    private val threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val notifierInjector = InjectableNotifierWrapper

    private val activeSyncThreadsWithSyncDirection = ConcurrentHashMap<Thread, SyncDirection>()
    private val tasks = ConcurrentLinkedQueue<SyncTask>()

    override fun close() {
        threadPool.shutdownNow()
    }

    fun enqueue(
        requiredLocks: LinkedHashSet<SyncLock>,
        syncDirection: SyncDirection,
        action: SyncTaskAction,
    ): ContinuableSyncTask {
        val task = SyncTask(requiredLocks, syncDirection, action)
        enqueue(task)
        return ContinuableSyncTask(task)
    }

    fun enqueue(task: SyncTask) {
        /*
         * Do not schedule Task if it is initiated on a Thread that is  running a synchronization and the sync direction
         * is the opposite of what is running on the thread already. This might be a symptom of a "table tennis"
         * (ping-pong) effect in which a change in MPS triggers a change in Modelix which triggers a change in MPS again
         * via the *ChangeListener and ModelixTreeChangeVisitor chains registered in MPS and in Modelix, respectively.
         *
         * Because the SyncTasks are executed on separate threads by the ExecutorService (see SyncTaskExecutors),
         * there is a very little chance of missing an intended change on other side. With other words: there is very
         * little chance that it makes sense that on the same thread two SyncTasks occur.
         */
        val taskSyncDirection = task.syncDirection
        val runningSyncDirection = activeSyncThreadsWithSyncDirection[Thread.currentThread()]

        val noTaskIsRunning = runningSyncDirection == null
        val runningTaskDirectionIsTheSame = taskSyncDirection == runningSyncDirection
        val isNoneDirection = taskSyncDirection == SyncDirection.NONE || runningSyncDirection == SyncDirection.NONE
        if (noTaskIsRunning || isNoneDirection || runningTaskDirectionIsTheSame) {
            enqueueAndFlush(task)
        } else {
            task.result.completeWithDefault()
        }
    }

    private fun enqueueAndFlush(task: SyncTask) {
        tasks.add(task)
        try {
            scheduleFlush()
        } catch (t: Throwable) {
            if (!threadPool.isShutdown) {
                val message =
                    "Task is cancelled, because an Exception occurred in the ThreadPool of the SyncQueue. Cause: ${t.message}"
                notifierInjector.notifyAndLogError(message, t, logger)
            }
            task.result.completeExceptionally(t)
        }
    }

    private fun scheduleFlush() {
        threadPool.submit {
            try {
                doFlush()
            } catch (t: Throwable) {
                val message =
                    "Running the SyncQueue Tasks on Thread ${Thread.currentThread()} failed. Cause: ${t.message}"
                notifierInjector.notifyAndLogError(message, t, logger)
            }
        }
    }

    private fun doFlush() {
        while (!tasks.isEmpty()) {
            val task = tasks.poll() ?: return
            runWithLocks(task.sortedLocks, task)
        }
    }

    private fun runWithLocks(locks: LinkedHashSet<SyncLock>, task: SyncTask) {
        val taskResult = task.result

        if (locks.isEmpty()) {
            // warning blocking call: if MPS gets frozen, this might be the reason
            val previousTaskResult = task.previousTaskResultHolder?.get()
            val result = task.action.invoke(previousTaskResult)
            if (result is CompletableFuture<*> && result.isCompletedExceptionally) {
                result.handle { _, throwable -> taskResult.completeExceptionally(throwable) }
            } else {
                taskResult.complete(result)
            }
        } else {
            val lockHeadAndTail = locks.customHeadTail()
            val lockHead = lockHeadAndTail.first

            runWithLock(lockHead) {
                val currentThread = Thread.currentThread()
                val wasAddedHere = !activeSyncThreadsWithSyncDirection.containsKey(currentThread)
                if (wasAddedHere) {
                    activeSyncThreadsWithSyncDirection[currentThread] = task.syncDirection
                }

                try {
                    val lockTail = lockHeadAndTail.second
                    runWithLocks(LinkedHashSet(lockTail), task)
                } catch (t: Throwable) {
                    val message = "Exception in task on $currentThread, Thread ID ${currentThread.id}."
                    logger.error(t) { message }

                    val wrapped = wrapErrorIntoSynchronizationException(t)
                    val cause = wrapped ?: t
                    notifierInjector.notifyAndLogError(cause.message ?: pleaseCheckLogs, cause, logger)

                    if (!taskResult.isCompletedExceptionally) {
                        taskResult.completeExceptionally(t)
                    }
                } finally {
                    if (wasAddedHere) {
                        // do not remove threads that were registered somewhere else
                        activeSyncThreadsWithSyncDirection.remove(currentThread)
                    }
                }
            }
        }
    }

    private fun runWithLock(lock: SyncLock, runnable: () -> Unit) {
        when (lock) {
            SyncLock.MPS_WRITE -> ActiveMpsProjectInjector.activeMpsProject!!.modelAccess.executeCommandInEDT(runnable)
            SyncLock.MPS_READ -> ActiveMpsProjectInjector.activeMpsProject!!.modelAccess.runReadAction(runnable)
            SyncLock.MODELIX_READ -> BranchRegistry.branch!!.runRead(runnable)
            SyncLock.MODELIX_WRITE -> BranchRegistry.branch!!.runWrite(runnable)
            SyncLock.NONE -> runnable.invoke()
        }
    }

    private fun wrapErrorIntoSynchronizationException(error: Throwable): SynchronizationException? {
        if (error is SynchronizationException) {
            return error
        }

        val headOfStackTrace = error.stackTrace.first()
        val className = headOfStackTrace.className.toLowerCase()
        return if (className.contains("mpsToModelix".toLowerCase())) {
            MpsToModelixSynchronizationException(error.message ?: pleaseCheckLogs, error)
        } else if (className.contains("modelixToMps".toLowerCase())) {
            ModelixToMpsSynchronizationException(error.message ?: pleaseCheckLogs, error)
        } else {
            null
        }
    }
}

// List.headTail does not work in some MPS versions (e.g. 2020.3.6), therefore we reimplemented the method
private fun <T> Iterable<T>.customHeadTail(): Pair<T, List<T>> = this.first() to this.drop(1)
