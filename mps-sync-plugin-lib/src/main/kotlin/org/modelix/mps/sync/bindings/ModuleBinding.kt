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

package org.modelix.mps.sync.bindings

import jetbrains.mps.project.AbstractModule
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.ModuleChangeListener
import org.modelix.mps.sync.util.completeWithDefault
import org.modelix.mps.sync.util.waitForCompletionOfEach
import java.util.concurrent.CompletableFuture

/**
 * An [IBinding] that represents that the corresponding [module] is bound to a Module on the model server by
 * having a [ModuleChangeListener] in the [AbstractModule]. These listener plays the changes in to the model server so
 * the local and remote states are kept in-sync.
 *
 * @param branch the modelix branch to where the changes will be synced to.
 * @param serviceLocator helps with initializing the private fields of this object.
 *
 * @property module the [AbstractModule] to which this binding belongs.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModuleBinding(val module: AbstractModule, branch: IBranch, serviceLocator: ServiceLocator) : IBinding {

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * The lookup map (internal cache) between the MPS elements and the corresponding modelix Nodes.
     */
    private val nodeMap = serviceLocator.nodeMap

    /**
     * The task queue of the sync plugin.
     */
    private val syncQueue = serviceLocator.syncQueue

    /**
     * The Futures queue of the sync plugin.
     */
    private val futuresWaitQueue = serviceLocator.futuresWaitQueue

    /**
     * The registry to store the [IBinding]s.
     */
    private val bindingsRegistry = serviceLocator.bindingsRegistry

    /**
     * A notifier that can notify the user about certain messages in a nicer way than just simply logging the message.
     */
    private val notifier = serviceLocator.wrappedNotifier

    /**
     * The change listener that listens to changes in the [AbstractModule].
     */
    private val changeListener = ModuleChangeListener(branch, serviceLocator)

    /**
     * Shows if the [ModuleBinding] has been disposed (deactivated). A disposed [ModuleBinding] does not do anything.
     *
     * See [deactivate] to know when a [ModuleBinding] is disposed.
     */
    @Volatile
    private var isDisposed = false

    /**
     * Shows if the [ModuleBinding] is activated. A [ModuleBinding] is activated if it is not disposed
     * (c.f. [isDisposed]), and its change listener is registered.
     */
    @Volatile
    private var isActivated = false

    /**
     * Activates the [ModuleBinding] by registering a change listener in the corresponding [AbstractModule]. After the
     * activation, the parameter 'callback' is called.
     *
     * @param callback some action to run after the activation of the [ModuleBinding].
     */
    @Synchronized
    override fun activate(callback: Runnable?) {
        if (isDisposed || isActivated) {
            return
        }

        // register module listener
        module.addModuleListener(changeListener)

        // activate child models' bindings
        bindingsRegistry.getModelBindings(module)?.forEach { it.activate() }

        isActivated = true

        bindingsRegistry.bindingActivated(this)

        val message = "${name()} is activated."
        notifier.notifyAndLogInfo(message, logger)

        callback?.run()
    }

    /**
     * Deactivates the [ModuleBinding]. A [ModuleBinding] is deactivated ([isDisposed]), if:
     *   1. all its [ModelBinding]s are deactivated,
     *   2. the [changeListener] is removed from the [module],
     *   3. the [ModuleBinding] is removed from the [bindingsRegistry],
     *   4. the [module] is removed from the server or its files are deleted locally (or not),
     *   5. the [module] is removed from the [nodeMap].
     *
     *  After deactivation, the parameter [callback] is run.
     *
     * @param removeFromServer if true, then the corresponding [module] will be removed from the model server.
     * @param callback some action to run after the deactivation of the [ModuleBinding].
     *
     * @return a [CompletableFuture] so caller of this method does not have to wait for the completion of the method.
     */
    override fun deactivate(removeFromServer: Boolean, callback: Runnable?): CompletableFuture<Any?> {
        if (isDisposed) {
            callback?.run()
            return CompletableFuture<Any?>().completeWithDefault()
        }

        return syncQueue.enqueue(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
            synchronized(this) {
                if (isActivated) {
                    // unregister listener
                    module.removeModuleListener(changeListener)

                    val modelBindings = bindingsRegistry.getModelBindings(module)

                    /*
                     * deactivate child models' bindings and wait for their successful completion
                     * throws ExecutionException if any deactivation failed
                     */
                    return@enqueue modelBindings?.waitForCompletionOfEach(futuresWaitQueue) {
                        it.deactivate(removeFromServer)
                    }
                }
            }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
            synchronized(this) {
                /*
                 * delete the binding, because if binding exists then module is assumed to exist,
                 * i.e. RepositoryChangeListener.moduleRemoved(...) will not delete the module
                 */
                bindingsRegistry.removeModuleBinding(this)
                isActivated = false
            }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
            nodeMap.remove(module)

            isDisposed = true

            val message = "${name()} is deactivated${
                if (removeFromServer) {
                    " and module is removed from the server"
                } else {
                    ""
                }
            }."
            notifier.notifyAndLogInfo(message, logger)

            callback?.run()
        }.getResult()
    }

    /**
     * @return the name of the [ModuleBinding].
     */
    override fun name() = "Binding of Module '${module.moduleName}'"

    /**
     * @see [name]
     */
    override fun toString() = name()
}
