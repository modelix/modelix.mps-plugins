package org.modelix.model.mpsplugin

import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.SetSequence
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import java.util.Collections

/*Generated by MPS */
class SyncTask(
    val binding: Binding,
    val direction: SyncDirection,
    val isInitialSync: Boolean,
    requiredLocks: Set<ELockType>?,
    private val implementation: Runnable
) : Runnable {
    val requiredLocks: Set<ELockType>
    private val callbacks: List<Runnable> = ListSequence.fromList(ArrayList())
    private var state: State = State.NEW

    init {
        this.requiredLocks = Collections.unmodifiableSet(SetSequence.fromSetWithValues(HashSet(), requiredLocks))
    }

    public override fun toString(): String {
        return "task[" + binding + ", " + direction + ", " + requiredLocks + "]"
    }

    @Synchronized
    public override fun run() {
        if (state != State.NEW) {
            throw IllegalStateException("Current state: " + state)
        }
        try {
            state = State.RUNNING
            if (binding.isActive) {
                binding.runningTask = this
                implementation.run()
            } else {
                if (LOG.isEnabledFor(Level.WARN)) {
                    LOG.warn("Skipped " + this + ", because the binding is inactive")
                }
                return
            }
        } catch (ex: Throwable) {
            LOG.error("Task failed: " + this, ex)
        } finally {
            binding.runningTask = null
            state = State.DONE
        }
    }

    fun invokeCallbacks() {
        for (callback: Runnable in ListSequence.fromList(callbacks)) {
            try {
                callback.run()
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }
    }

    val isDone: Boolean
        get() {
            return state == State.DONE
        }
    val isRunning: Boolean
        get() {
            return state == State.RUNNING
        }

    fun whenDone(callback: Runnable?) {
        if (callback != null) {
            ListSequence.fromList(callbacks).addElement(callback)
        }
    }

    private enum class State {
        NEW,
        RUNNING,
        DONE
    }

    companion object {
        private val LOG: Logger = LogManager.getLogger(SyncTask::class.java)
    }
}