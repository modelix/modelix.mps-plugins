/*
 * Copyright (c) 2023-2024.
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

package org.modelix.mps.sync.transformation.cache

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.mps.services.InjectableService
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.util.synchronizedLinkedHashSet
import org.modelix.mps.sync.util.synchronizedMap

/**
 * ⚠️ WARNING ⚠️:
 * - use with caution, otherwise this cache may cause memory leaks
 * - if you add a new Map as a field in the class, then please also add it to the [remove], [isMappedToMps],
 * [isMappedToModelix], [clear] methods below.
 * - if you want to persist the new field into a file, then add it to the [MpsToModelixMap.Serializer.serialize] and
 * [MpsToModelixMap.Serializer.deserialize] methods below.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class MpsToModelixMap : InjectableService {

    private val nodeToModelixId = synchronizedMap<SNodeReference, Long>()
    private val modelixIdToNode = synchronizedMap<Long, SNodeReference>()

    private val modelToModelixId = synchronizedMap<SModelReference, Long>()
    private val modelixIdToModel = synchronizedMap<Long, SModelReference>()

    private val moduleToModelixId = synchronizedMap<SModuleReference, Long>()
    private val modelixIdToModule = synchronizedMap<Long, SModuleReference>()

    private val moduleWithOutgoingModuleReferenceToModelixId = synchronizedMap<ModuleWithModuleReference, Long>()
    private val modelixIdToModuleWithOutgoingModuleReference = synchronizedMap<Long, ModuleWithModuleReference>()

    private val modelWithOutgoingModuleReferenceToModelixId = synchronizedMap<ModelWithModuleReference, Long>()
    private val modelixIdToModelWithOutgoingModuleReference = synchronizedMap<Long, ModelWithModuleReference>()

    private val modelWithOutgoingModelReferenceToModelixId = synchronizedMap<ModelWithModelReference, Long>()
    private val modelixIdToModelWithOutgoingModelReference = synchronizedMap<Long, ModelWithModelReference>()

    private val objectsRelatedToAModel = synchronizedMap<SModelReference, MutableSet<Any>>()
    private val objectsRelatedToAModule = synchronizedMap<SModuleReference, MutableSet<Any>>()

    /**
     * The active [SRepository] to access the [org.jetbrains.mps.openapi.model.SModel]s and
     * [org.jetbrains.mps.openapi.module.SModule]s in MPS.
     */
    private val mpsRepository: SRepository
        get() = serviceLocator.mpsRepository

    /**
     * A collector class to simplify injecting the commonly used services in the sync plugin.
     */
    private lateinit var serviceLocator: ServiceLocator

    override fun initService(serviceLocator: ServiceLocator) {
        this.serviceLocator = serviceLocator
    }

    fun put(node: SNode, modelixId: Long) {
        val nodeReference = node.reference
        nodeToModelixId[nodeReference] = modelixId
        modelixIdToNode[modelixId] = nodeReference

        node.model?.let { putObjRelatedToAModel(it, nodeReference) }
    }

    fun put(model: SModel, modelixId: Long) {
        val modelReference = model.reference
        modelToModelixId[modelReference] = modelixId
        modelixIdToModel[modelixId] = modelReference

        putObjRelatedToAModel(model, modelReference)
    }

    fun put(module: SModule, modelixId: Long) {
        val moduleReference = module.moduleReference
        moduleToModelixId[moduleReference] = modelixId
        modelixIdToModule[modelixId] = moduleReference

        putObjRelatedToAModule(module, moduleReference)
    }

    fun put(sourceModule: SModule, moduleReference: SModuleReference, modelixId: Long) {
        val moduleWithOutgoingModuleReference = ModuleWithModuleReference(sourceModule, moduleReference)
        moduleWithOutgoingModuleReferenceToModelixId[moduleWithOutgoingModuleReference] = modelixId
        modelixIdToModuleWithOutgoingModuleReference[modelixId] = moduleWithOutgoingModuleReference

        putObjRelatedToAModule(sourceModule, moduleReference)
    }

    fun put(sourceModel: SModel, moduleReference: SModuleReference, modelixId: Long) {
        val modelWithOutgoingModuleReference = ModelWithModuleReference(sourceModel, moduleReference)
        modelWithOutgoingModuleReferenceToModelixId[modelWithOutgoingModuleReference] = modelixId
        modelixIdToModelWithOutgoingModuleReference[modelixId] = modelWithOutgoingModuleReference

        putObjRelatedToAModel(sourceModel, moduleReference)
    }

    fun put(sourceModel: SModel, modelReference: SModelReference, modelixId: Long) {
        val modelWithOutgoingModelReference = ModelWithModelReference(sourceModel, modelReference)
        modelWithOutgoingModelReferenceToModelixId[modelWithOutgoingModelReference] = modelixId
        modelixIdToModelWithOutgoingModelReference[modelixId] = modelWithOutgoingModelReference

        putObjRelatedToAModel(sourceModel, modelReference)
    }

    private fun putObjRelatedToAModel(model: SModel, obj: Any) {
        val modelReference = model.reference
        objectsRelatedToAModel.computeIfAbsent(modelReference) { synchronizedLinkedHashSet() }.add(obj)
        // just in case, the model has not been tracked yet. E.g. @descriptor models that are created locally but were not synchronized to the model server.
        putObjRelatedToAModule(model.module, modelReference)
    }

    private fun putObjRelatedToAModule(module: SModule, obj: Any) =
        objectsRelatedToAModule.computeIfAbsent(module.moduleReference) { synchronizedLinkedHashSet() }.add(obj)

    operator fun get(node: SNode?) = nodeToModelixId[node?.reference]

    operator fun get(model: SModel?) = modelToModelixId[model?.reference]

    operator fun get(modelId: SModelId?) =
        modelToModelixId.filter { it.key.modelId == modelId }.map { it.value }.firstOrNull()

    operator fun get(module: SModule?) = moduleToModelixId[module?.moduleReference]

    operator fun get(moduleId: SModuleId?) =
        moduleToModelixId.filter { it.key.moduleId == moduleId }.map { it.value }.firstOrNull()

    operator fun get(sourceModel: SModel, moduleReference: SModuleReference) =
        modelWithOutgoingModuleReferenceToModelixId[ModelWithModuleReference(sourceModel, moduleReference)]

    operator fun get(sourceModule: SModule, moduleReference: SModuleReference) =
        moduleWithOutgoingModuleReferenceToModelixId[ModuleWithModuleReference(sourceModule, moduleReference)]

    operator fun get(sourceModel: SModel, modelReference: SModelReference) =
        modelWithOutgoingModelReferenceToModelixId[ModelWithModelReference(sourceModel, modelReference)]

    fun getNode(modelixId: Long?): SNode? = modelixIdToNode[modelixId]?.resolve(mpsRepository)

    fun getModel(modelixId: Long?): SModel? = modelixIdToModel[modelixId]?.resolve(mpsRepository)

    fun getModule(modelixId: Long?): SModule? = modelixIdToModule[modelixId]?.resolve(mpsRepository)

    fun getModule(moduleId: SModuleId): SModule? =
        objectsRelatedToAModule.keys.firstOrNull { it.moduleId == moduleId }?.resolve(mpsRepository)

    fun getOutgoingModelReference(modelixId: Long?) = modelixIdToModelWithOutgoingModelReference[modelixId]

    fun getOutgoingModuleReferenceFromModel(modelixId: Long?) = modelixIdToModelWithOutgoingModuleReference[modelixId]

    fun getOutgoingModuleReferenceFromModule(modelixId: Long?) = modelixIdToModuleWithOutgoingModuleReference[modelixId]

    fun remove(modelixId: Long) {
        modelixIdToNode.remove(modelixId)?.let { nodeToModelixId.remove(it) }

        modelixIdToModel.remove(modelixId)?.let {
            modelToModelixId.remove(it)
            it.resolve(mpsRepository)?.let { model -> remove(model) }
        }

        modelixIdToModelWithOutgoingModelReference[modelixId]?.let { remove(it) }
        modelixIdToModelWithOutgoingModuleReference[modelixId]?.let { remove(it) }

        modelixIdToModule.remove(modelixId)?.let { it.resolve(mpsRepository)?.let { module -> remove(module) } }
        modelixIdToModuleWithOutgoingModuleReference[modelixId]?.let { remove(it) }
    }

    fun remove(model: SModel) {
        val reference = model.reference
        modelToModelixId.remove(reference)?.let { modelixIdToModel.remove(it) }
        objectsRelatedToAModel.remove(reference)?.forEach {
            when (it) {
                is SModuleReference -> {
                    val target = ModelWithModuleReference(model, it)
                    remove(target)
                }

                is SModelReference -> {
                    val target = ModelWithModelReference(model, it)
                    remove(target)
                }

                is SNodeReference -> {
                    nodeToModelixId.remove(it)?.let { modelixId -> modelixIdToNode.remove(modelixId) }
                }
            }
        }
    }

    fun remove(module: SModule) {
        val reference = module.moduleReference
        moduleToModelixId.remove(reference)?.let { modelixIdToModule.remove(it) }
        objectsRelatedToAModule.remove(reference)?.forEach {
            if (it is SModuleReference) {
                val target = ModuleWithModuleReference(module, it)
                remove(target)
            } else if (it is SModelReference) {
                it.resolve(mpsRepository)?.let { model -> remove(model) }
            }
        }
    }

    fun remove(outgoingModelReference: ModelWithModelReference) {
        modelWithOutgoingModelReferenceToModelixId.remove(outgoingModelReference)
            ?.let { id -> modelixIdToModelWithOutgoingModelReference.remove(id) }
    }

    fun remove(modelWithModuleReference: ModelWithModuleReference) {
        modelWithOutgoingModuleReferenceToModelixId.remove(modelWithModuleReference)
            ?.let { id -> modelixIdToModelWithOutgoingModuleReference.remove(id) }
    }

    fun remove(moduleWithModuleReference: ModuleWithModuleReference) {
        moduleWithOutgoingModuleReferenceToModelixId.remove(moduleWithModuleReference)
            ?.let { id -> modelixIdToModuleWithOutgoingModuleReference.remove(id) }
    }

    fun isMappedToMps(modelixId: Long?): Boolean {
        if (modelixId == null) {
            return false
        }
        val idMaps = arrayOf(
            modelixIdToNode,
            modelixIdToModel,
            modelixIdToModule,
            modelixIdToModuleWithOutgoingModuleReference,
            modelixIdToModelWithOutgoingModuleReference,
            modelixIdToModelWithOutgoingModelReference,
        )

        for (idMap in idMaps) {
            if (idMap.contains(modelixId)) {
                return true
            }
        }
        return false
    }

    fun isMappedToModelix(model: SModel) = this[model] != null

    fun isMappedToModelix(module: SModule) = this[module] != null

    fun isMappedToModelix(node: SNode) = this[node] != null

    override fun dispose() {
        nodeToModelixId.clear()
        modelixIdToNode.clear()
        modelToModelixId.clear()
        modelixIdToModel.clear()
        moduleToModelixId.clear()
        modelixIdToModule.clear()
        moduleWithOutgoingModuleReferenceToModelixId.clear()
        modelixIdToModuleWithOutgoingModuleReference.clear()
        modelWithOutgoingModuleReferenceToModelixId.clear()
        modelixIdToModelWithOutgoingModuleReference.clear()
        modelWithOutgoingModelReferenceToModelixId.clear()
        modelixIdToModelWithOutgoingModelReference.clear()
        objectsRelatedToAModel.clear()
        objectsRelatedToAModule.clear()
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class ModelWithModelReference(
    val sourceModelReference: SModelReference,
    val modelReference: SModelReference,
) {
    constructor(source: SModel, modelReference: SModelReference) : this(source.reference, modelReference)
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class ModelWithModuleReference(
    val sourceModelReference: SModelReference,
    val moduleReference: SModuleReference,
) {
    constructor(source: SModel, moduleReference: SModuleReference) : this(source.reference, moduleReference)
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class ModuleWithModuleReference(
    val sourceModuleReference: SModuleReference,
    val moduleReference: SModuleReference,
) {
    constructor(source: SModule, moduleReference: SModuleReference) : this(source.moduleReference, moduleReference)
}
