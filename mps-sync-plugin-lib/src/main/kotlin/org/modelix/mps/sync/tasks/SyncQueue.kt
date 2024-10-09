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

import com.intellij.openapi.application.ApplicationManager
import jetbrains.mps.project.MPSProject
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.mps.sync.modelix.branch.BranchRegistry
import org.modelix.mps.sync.mps.notifications.WrappedNotifier
import org.modelix.mps.sync.mps.services.InjectableService
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.mps.util.runReadAction
import org.modelix.mps.sync.transformation.exceptions.ModelixToMpsSynchronizationException
import org.modelix.mps.sync.transformation.exceptions.MpsToModelixSynchronizationException
import org.modelix.mps.sync.transformation.exceptions.SynchronizationException
import org.modelix.mps.sync.transformation.exceptions.pleaseCheckLogs
import org.modelix.mps.sync.transformation.modelixToMps.incremental.ModelixTreeChangeVisitor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A queue for executing [SyncTask] with its required locks asynchronously on a [threadPool].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class SyncQueue : InjectableService {

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * An application-level shared fixed size thread pool. We run the [tasks] in this thread pool.
     */
    private val threadPool = ApplicationManager.getApplication().getService(SharedThreadPool::class.java)

    /**
     * Keeps track of the active [SyncDirection] for each [Thread] that is running a [SyncTask]. Per [Thread] only one
     * [SyncTask] is allowed and each [SyncTask] has only one [SyncDirection].
     *
     * This tracking is necessary, because we want to avoid the table tennis ("ping-pong") effect between modelix model
     * server and MPS if a change on either side occurs. For details see [enqueue].
     */
    private val activeSyncThreadsWithSyncDirection = ConcurrentHashMap<Thread, SyncDirection>()

    /**
     * The queue of [SyncTask]s we want to execute.
     */
    private val tasks = ConcurrentLinkedQueue<SyncTask>()

    /**
     * A notifier that can notify the user about certain messages in a nicer way than just simply logging the message.
     */
    private val notifier: WrappedNotifier
        get() = serviceLocator.wrappedNotifier

    /**
     * A registry to store the modelix [IBranch] we are connected to.
     */
    private val branchRegistry: BranchRegistry
        get() = serviceLocator.branchRegistry

    /**
     * The [MPSProject] that is open in the active MPS window.
     */
    private val mpsProject: MPSProject
        get() = serviceLocator.mpsProject

    /**
     * The Futures queue of the sync plugin.
     */
    private val futuresWaitQueue: FuturesWaitQueue
        get() = serviceLocator.futuresWaitQueue

    /**
     * A collector class to simplify injecting the commonly used services in the sync plugin.
     */
    private lateinit var serviceLocator: ServiceLocator

    override fun initService(serviceLocator: ServiceLocator) {
        this.serviceLocator = serviceLocator
    }

    /**
     * Creates a [SyncTask] from the parameters and adds it to the [tasks] queue.
     *
     * @param requiredLocks the locks used by the [SyncTask].
     * @param syncDirection the synchronization direction of the [SyncTask].
     * @param action the action to do in the [SyncTask].
     *
     * @return a [ContinuableSyncTask] so that we can chain [SyncTask]s after each other.
     */
    fun enqueue(
        requiredLocks: LinkedHashSet<SyncLock>,
        syncDirection: SyncDirection,
        action: SyncTaskAction,
    ): ContinuableSyncTask {
        val task = SyncTask(requiredLocks, syncDirection, action)
        enqueue(task)
        return ContinuableSyncTask(task, this, futuresWaitQueue)
    }

    /**
     * Enqueues the [task] into the [tasks] queue.
     *
     * The method does not enqueue the [task] if it is initiated on a [Thread] that is running a synchronization and the
     * sync direction is the opposite of what is running on the [Thread] already. This might be a symptom of a
     * "table tennis" (ping-pong) effect in which a change in MPS triggers a change in modelix which triggers a change
     * in MPS again via the *ChangeListener and [ModelixTreeChangeVisitor] chains registered in MPS and in modelix,
     * respectively.
     *
     * Because the [SyncTask]s are executed on separate [Thread]s by the [ExecutorService], there is a very little
     * chance of missing an intended change on other side. With other words: there is very little chance that it makes
     * sense that on the same [Thread] two [SyncTask]s occur.
     */
    fun enqueue(task: SyncTask) {
        val taskSyncDirection = task.syncDirection
        val runningSyncDirection = activeSyncThreadsWithSyncDirection[Thread.currentThread()]

        val noTaskIsRunning = runningSyncDirection == null
        val runningTaskDirectionIsTheSame = taskSyncDirection == runningSyncDirection
        val isNoneDirection = taskSyncDirection == SyncDirection.NONE || runningSyncDirection == SyncDirection.NONE
        if (noTaskIsRunning || isNoneDirection || runningTaskDirectionIsTheSame) {
            enqueueAndFlush(task)
        } else {
            // Fail the result in this case, because the follower tasks assume that its predecessor task ran.
            task.result.completeExceptionally(IllegalStateException("Predecessor task was not scheduled in the SyncQueue."))
        }
    }

    /**
     * Enqueues the [task] into the [tasks] queue and schedules its flush so the [task] will get executed.
     *
     * @param task the [SyncTask] to enqueue and execute.
     */
    private fun enqueueAndFlush(task: SyncTask) {
        tasks.add(task)
        try {
            scheduleFlush()
        } catch (t: Throwable) {
            if (!threadPool.isShutdown) {
                val message =
                    "Task is cancelled, because an Exception occurred in the ThreadPool of the SyncQueue. Cause: ${t.message}"
                notifier.notifyAndLogError(message, t, logger)
            }
            task.result.completeExceptionally(t)
        }
    }

    /**
     * Schedules a flush on the [tasks] queue.
     */
    private fun scheduleFlush() {
        threadPool.submit {
            try {
                doFlush()
            } catch (t: Throwable) {
                val message =
                    "Running the SyncQueue Tasks on Thread ${Thread.currentThread()} failed. Cause: ${t.message}"
                notifier.notifyAndLogError(message, t, logger)
            }
        }
    }

    /**
     * Flushes the [tasks] queue. I.e. polls the queue and runs the task until there is no task left.
     */
    private fun doFlush() {
        while (!tasks.isEmpty()) {
            val task = tasks.poll() ?: return
            runWithLocks(task.sortedLocks, task)
        }
    }

    /**
     * Runs the [task] with the required synchronization [locks].
     *
     * @param locks the synchronization locks to acquire when running the [task].
     * @param task the task to run.
     */
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
                    notifier.notifyAndLogError(cause.message ?: pleaseCheckLogs, cause, logger)

                    if (!taskResult.isCompletedExceptionally) {
                        taskResult.completeExceptionally(cause)
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

    /**
     * Runs the [runnable] with the given [lock].
     *
     * @param lock the lock to acquire before running the [runnable].
     * @param runnable the action to run with the lock.
     */
    private fun runWithLock(lock: SyncLock, runnable: () -> Unit) {
        when (lock) {
            SyncLock.MPS_WRITE -> mpsProject.modelAccess.executeCommandInEDT(runnable)
            SyncLock.MPS_READ -> mpsProject.runReadAction { runnable() }
            SyncLock.MODELIX_READ -> getModelixBranch().runRead(runnable)
            SyncLock.MODELIX_WRITE -> getModelixBranch().runWrite(runnable)
            SyncLock.NONE -> runnable.invoke()
        }
    }

    /**
     * @param error the error to wrap into the [SynchronizationException]
     *
     * @return [SynchronizationException] if the first item of the stack trace inside the [error] is a class from the
     * "mpsToModelix" or "modelixToMps" packages. Otherwise, it returns null.
     */
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

    /**
     * @throws IllegalStateException if the branch is null.
     *
     * @see [BranchRegistry.getBranch].
     */
    @Throws(IllegalStateException::class)
    private fun getModelixBranch() = branchRegistry.getBranch() ?: throw IllegalStateException("Branch is null.")
}

/**
 * List.headTail does not work in some MPS versions (e.g. 2020.3.6), therefore we reimplemented the method.
 *
 * @return a [Pair] whose first element is the first item of the [Iterable], the second item is the rest of the
 * [Iterable].
 */
private fun <T> Iterable<T>.customHeadTail(): Pair<T, List<T>> = this.first() to this.drop(1)
