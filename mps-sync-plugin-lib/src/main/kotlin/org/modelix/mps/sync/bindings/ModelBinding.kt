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

import jetbrains.mps.extapi.model.SModelBase
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.tasks.SyncDirection
import org.modelix.mps.sync.tasks.SyncLock
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.ModelChangeListener
import org.modelix.mps.sync.transformation.mpsToModelix.incremental.NodeChangeListener
import org.modelix.mps.sync.util.completeWithDefault
import java.util.concurrent.CompletableFuture

/**
 * An [IBinding] that represents that the corresponding [model] is bound to a Model on the model server by having
 * a [ModelChangeListener] and a [NodeChangeListener] registered in the [SModelBase]. These listeners play the changes
 * in to the model server so the local and remote states are kept in-sync.
 *
 * @param branch the modelix branch to where the changes will be synced to.
 * @param serviceLocator helps with initializing the private fields of this object.
 *
 * @property model the [SModelBase] to which this binding belongs.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelBinding(val model: SModelBase, branch: IBranch, serviceLocator: ServiceLocator) : IBinding {

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
     * The registry to store the [IBinding]s.
     */
    private val bindingsRegistry = serviceLocator.bindingsRegistry

    /**
     * A notifier that can notify the user about certain messages in a nicer way than just simply logging the message.
     */
    private val notifier = serviceLocator.wrappedNotifier

    /**
     * The change listener that listens to changes in the [SModelBase].
     */
    private val modelChangeListener = ModelChangeListener(this, branch, serviceLocator)

    /**
     * The change listener that listens to changes in the SNodes of the [SModelBase].
     */
    private val nodeChangeListener = NodeChangeListener(branch, serviceLocator)

    /**
     * Shows if the [ModelBinding] has been disposed (deactivated). A disposed [ModelBinding] does not do anything.
     *
     * See [deactivate] to know when a [ModelBinding] is disposed.
     */
    @Volatile
    private var isDisposed = false

    /**
     * Shows if the [ModelBinding] is activated. A [ModelBinding] is activated if it is not disposed
     * (c.f. [isDisposed]), and all its change listeners are registered.
     */
    @Volatile
    private var isActivated = false

    /**
     * Activates the [ModelBinding] by registering some change listeners in the corresponding [SModelBase]. After the
     * activation, the parameter 'callback' is called.
     *
     * @param callback some action to run after the activation of the [ModelBinding].
     */
    @Synchronized
    override fun activate(callback: Runnable?) {
        if (isDisposed || isActivated) {
            return
        }

        // register listeners
        model.addChangeListener(nodeChangeListener)
        model.addModelListener(modelChangeListener)

        isActivated = true

        bindingsRegistry.bindingActivated(this)

        val message = "${name()} is activated."
        notifier.notifyAndLogInfo(message, logger)

        callback?.run()
    }

    /**
     * Deactivates the [ModelBinding]. A [ModelBinding] is deactivated ([isDisposed]), if:
     *   1. the [modelChangeListener] and [nodeChangeListener] changes listeners are removed from the [model],
     *   2. the [model] is removed from the server or its files are deleted locally (or not),
     *   3. the [model] is removed from the server or its files are deleted locally (or not),
     *   4. the [model] is removed from the [nodeMap].
     *
     * After deactivation, the parameter [callback] is run.
     *
     * @param removeFromServer if true, then the corresponding [model] will be removed from the model server.
     * @param callback some action to run after the deactivation of the [ModelBinding].
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
                    // unregister listeners
                    model.removeChangeListener(nodeChangeListener)
                    model.removeModelListener(modelChangeListener)

                    if (removeFromServer) {
                        /*
                         * remove from bindings, so when removing the model from the module we'll know that this model
                         * is not assumed to exist, therefore we'll not delete it in the cloud
                         * (see ModuleChangeListener's modelRemoved method)
                         */
                        bindingsRegistry.removeModelBinding(this)
                    }

                    isActivated = false
                }
            }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
            bindingsRegistry.removeModelBinding(this)

            if (!removeFromServer) {
                /*
                 * when deleting the model (modelix Node) from the cloud, then the NodeSynchronizer.removeNode takes
                 * care of the node deletion
                 */
                nodeMap.remove(model)
            }

            isDisposed = true

            val message = "${name()} is deactivated${
                if (removeFromServer) {
                    " and is removed from the server"
                } else {
                    ""
                }
            }."
            notifier.notifyAndLogInfo(message, logger)

            callback?.run()
        }.getResult()
    }

    /**
     * @return the name of the [ModelBinding].
     */
    override fun name() = "Binding of Model '${model.name}'"

    /**
     * @see [name]
     */
    override fun toString() = name()
}
