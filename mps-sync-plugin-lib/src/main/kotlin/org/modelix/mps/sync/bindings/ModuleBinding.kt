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

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class ModuleBinding(val module: AbstractModule, branch: IBranch, serviceLocator: ServiceLocator) : IBinding {

    private val logger = KotlinLogging.logger {}

    private val nodeMap = serviceLocator.nodeMap
    private val syncQueue = serviceLocator.syncQueue
    private val futuresWaitQueue = serviceLocator.futuresWaitQueue

    private val bindingsRegistry = serviceLocator.bindingsRegistry
    private val notifier = serviceLocator.wrappedNotifier

    private val changeListener = ModuleChangeListener(branch, serviceLocator)

    @Volatile
    private var isDisposed = false

    @Volatile
    private var isActivated = false

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

            val message = "${name()} is deactivated and module is removed locally${
                if (removeFromServer) {
                    " and from server"
                } else {
                    ""
                }
            }."
            notifier.notifyAndLogInfo(message, logger)

            callback?.run()
        }.getResult()
    }

    override fun name() = "Binding of Module '${module.moduleName}'"

    override fun toString() = name()
}
