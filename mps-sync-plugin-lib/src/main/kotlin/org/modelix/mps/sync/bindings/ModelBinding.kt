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

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModelBinding(val model: SModelBase, branch: IBranch, serviceLocator: ServiceLocator) : IBinding {

    private val logger = KotlinLogging.logger {}

    private val nodeMap = serviceLocator.nodeMap
    private val syncQueue = serviceLocator.syncQueue

    private val bindingsRegistry = serviceLocator.bindingsRegistry
    private val notifier = serviceLocator.wrappedNotifier

    private val modelChangeListener = ModelChangeListener(this, branch, serviceLocator)
    private val nodeChangeListener = NodeChangeListener(branch, serviceLocator)

    @Volatile
    private var isDisposed = false

    @Volatile
    private var isActivated = false

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
                        bindingsRegistry.removeModelBinding(model.module!!, this)
                    }

                    isActivated = false
                }
            }
        }.continueWith(linkedSetOf(SyncLock.NONE), SyncDirection.NONE) {
            bindingsRegistry.removeModelBinding(model.module!!, this)

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

    override fun name() = "Binding of Model '${model.name}'"

    override fun toString() = name()
}
