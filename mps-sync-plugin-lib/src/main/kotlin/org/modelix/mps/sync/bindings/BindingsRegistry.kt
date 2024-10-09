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
import jetbrains.mps.project.AbstractModule
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.mps.services.InjectableService
import org.modelix.mps.sync.util.completeWithDefault
import org.modelix.mps.sync.util.synchronizedLinkedHashSet
import org.modelix.mps.sync.util.synchronizedMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.function.BiConsumer

/**
 * This service can be used to store [ModelBinding] and [ModuleBinding]s at a central place, so they can be:
 *  - looked up by their [SModelBase], [SModelId] or [SModule],
 *  - deactivated at once,
 *  - their lifecycle state (c.f. [BindingState]) can be tracked centrally.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class BindingsRegistry : InjectableService {

    /**
     * Group [ModelBinding]s by their [SModule]. So that we directly know which [ModelBinding]s were created to the
     * SModels inside the [SModule], without having to iterate through the SModels themselves.
     */
    private val modelBindingsByModule = synchronizedMap<SModule, MutableSet<ModelBinding>>()

    /**
     * A list of [ModuleBinding]s that are activated or will be activated in MPS.
     */
    private val moduleBindings = synchronizedLinkedHashSet<ModuleBinding>()

    /**
     * A list of [IBinding]s with their latest [BindingLifecycleState] stored together in a [BindingState]. It shows the
     * latest status of the respective binding.
     */
    val changedBindings = LinkedBlockingQueue<BindingState>()

    /**
     * Adds a [ModelBinding] to the registry, and puts it in [changedBindings] with the [BindingLifecycleState.ADD] state.
     *
     * @param binding the [ModelBinding] to be put in the registry.
     */
    fun addModelBinding(binding: ModelBinding) {
        modelBindingsByModule.computeIfAbsent(binding.model.module!!) { synchronizedLinkedHashSet() }.add(binding)
        bindingAdded(binding)
    }

    /**
     * Adds a [ModuleBinding] to the registry, and puts it in [changedBindings] with the [BindingLifecycleState.ADD] state.
     *
     * @param binding the [ModuleBinding] to be put in the registry.
     */
    fun addModuleBinding(binding: ModuleBinding) {
        moduleBindings.add(binding)
        bindingAdded(binding)
    }

    /**
     * Removes a [ModelBinding] from the registry, and puts it in [changedBindings] with the
     * [BindingLifecycleState.REMOVE] state.
     *
     * @param binding the [ModelBinding] to be removed from the registry.
     */
    fun removeModelBinding(binding: ModelBinding) {
        modelBindingsByModule[binding.model.module!!]?.remove(binding)
        bindingRemoved(binding)
    }

    /**
     * Removes a [ModuleBinding] from the registry and puts it in [changedBindings] with the
     * [BindingLifecycleState.REMOVE] state.
     *
     * @param binding the [ModuleBinding] to be removed from the registry.
     */
    fun removeModuleBinding(binding: ModuleBinding) {
        val module = binding.module
        check(
            modelBindingsByModule.getOrDefault(module, LinkedHashSet()).isEmpty(),
        ) { "$binding cannot be removed, because not all of its model' bindings have been removed." }

        modelBindingsByModule.remove(module)
        moduleBindings.remove(binding)
        bindingRemoved(binding)
    }

    /**
     * @return all [ModelBinding]s that are stored in the registry.
     */
    fun getModelBindings(): List<ModelBinding> =
        modelBindingsByModule.values.flatten().toCollection(mutableListOf()).toList()

    /**
     * @param module the [SModule] whose SModels have the [ModelBinding]s.
     *
     * @return all [ModelBinding]s that belong to the SModels of the given [SModule].
     */
    fun getModelBindings(module: SModule): Set<ModelBinding>? = modelBindingsByModule[module]?.toSet()

    /**
     * @return all [ModuleBinding]s that are stored in the registry.
     */
    fun getModuleBindings(): List<ModuleBinding> = moduleBindings.toList()

    /**
     * @param model the [SModelBase] whose [ModelBinding] we would like to get
     *
     * @return the [ModelBinding] that belongs to the given [SModelBase], or null if no such object exists.
     */
    fun getModelBinding(model: SModelBase) = getModelBindings().find { it.model == model }

    /**
     * @param modelId the [SModelId] of the SModel whose [ModelBinding] we would like to get
     *
     * @return the [ModelBinding] that belongs to the SModel with the given [SModelId], or null if no such object exists.
     */
    fun getModelBinding(modelId: SModelId) = getModelBindings().find { it.model.modelId == modelId }

    /**
     * @param module the [AbstractModule] whose [ModuleBinding] we would like to get
     *
     * @return the [ModuleBinding] that belongs to the given [AbstractModule], or null if no such object exists.
     */
    fun getModuleBinding(module: AbstractModule) = moduleBindings.find { it.module == module }

    /**
     * Adds a new [BindingState] to the [changedBindings] with the given binding and [BindingLifecycleState.ACTIVATE].
     *
     * @param binding the [IBinding] whose state we would like to set to [BindingLifecycleState.ACTIVATE].
     */
    fun bindingActivated(binding: IBinding) =
        changedBindings.put(BindingState(binding, BindingLifecycleState.ACTIVATE))

    /**
     * Deactivates all [IBinding]s that are stored in this registry.
     *
     * @param waitForCompletion if true, then this method will be a blocking call and wait for the deactivation of all
     * [IBinding]s that are stored in this registry. If false, then the method will immediately return after it
     * initiated the deactivation of the [IBinding]s.
     */
    fun deactivateBindings(waitForCompletion: Boolean = false) {
        val moduleBindings = getModuleBindings()
        val modelBindings = getModelBindings()

        if (!waitForCompletion) {
            moduleBindings.forEach { it.deactivate(removeFromServer = false) }
            modelBindings.forEach { it.deactivate(removeFromServer = false) }
            // return immediately, do not wait until the bindings are completely deactivated
            return
        }

        // wait until all module/model bindings are deactivated  or any of them throws an exception

        val countDownLatch = CountDownLatch(moduleBindings.size + modelBindings.size)
        val continuation = CompletableFuture<Any?>()
        if (countDownLatch.count == 0L) {
            continuation.completeWithDefault()
        }

        val countDownOrAbort = BiConsumer<Any?, Throwable?> { _, throwable ->
            if (throwable != null) {
                continuation.completeExceptionally(throwable)
            }
            countDownLatch.countDown()
            if (countDownLatch.count == 0L) {
                continuation.completeWithDefault()
            }
        }

        moduleBindings.forEach { it.deactivate(removeFromServer = false).whenComplete(countDownOrAbort) }
        modelBindings.forEach { it.deactivate(removeFromServer = false).whenComplete(countDownOrAbort) }

        continuation.get()
    }

    /**
     * Adds a new [BindingState] to the [changedBindings] with the given binding and [BindingLifecycleState.ADD].
     *
     * @param binding the [IBinding] whose state we would like to set to [BindingLifecycleState.ADD].
     */
    private fun bindingAdded(binding: IBinding) =
        changedBindings.put(BindingState(binding, BindingLifecycleState.ADD))

    /**
     * Adds a new [BindingState] to the [changedBindings] with the given binding and [BindingLifecycleState.REMOVE].
     *
     * @param binding the [IBinding] whose state we would like to set to [BindingLifecycleState.REMOVE].
     */
    private fun bindingRemoved(binding: IBinding) =
        changedBindings.put(BindingState(binding, BindingLifecycleState.REMOVE))

    /**
     * Deactivates all [IBinding]s that are stored in this registry.
     *
     * @see [deactivateBindings]
     */
    override fun dispose() {
        deactivateBindings()
    }
}

/**
 * Represents an [IBinding] that is in the given [BindingLifecycleState] at the moment.
 *
 * @property binding the [IBinding] whose lifecycle state we have changed.
 * @property state the [BindingLifecycleState] lifecycle state of the binding.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class BindingState(
    val binding: IBinding,
    val state: BindingLifecycleState,
)

/**
 * The possible lifecycle states of an [IBinding]:
 *   - [ADD]: the [IBinding] is added to the [BindingsRegistry]
 *   - [REMOVE]: the [IBinding] is removed from the [BindingsRegistry]
 *   - [ACTIVATE]: the [IBinding] is activated, i.e. its [IBinding.activate] has been called
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
enum class BindingLifecycleState {
    ADD,
    REMOVE,
    ACTIVATE,
}
