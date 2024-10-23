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
 * The lookup map (internal cache) between the MPS elements and the corresponding modelix nodes. We use this map so
 * that we can switch between the MPS and modelix worlds quickly.
 *
 * ⚠️ WARNING ⚠️:
 *   - use with caution, otherwise this cache may cause memory leaks, if the elements are not removed when not needed
 * anymore.
 *   - if you add a new Map as a field in the class, then please also add it to the [remove], [isMappedToMps],
 * [isMappedToModelix], [clear] methods below.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class MpsToModelixMap : InjectableService {

    /**
     * The mapping between SNodeReferences (SNodes) and the corresponding modelix node IDs.
     *
     * The field is the inverse of [modelixIdToNode].
     */
    private val nodeToModelixId = synchronizedMap<SNodeReference, Long>()

    /**
     * The mapping between the modelix node IDs and the corresponding SNodeReferences (SNodes).
     *
     * The field is the inverse of [nodeToModelixId].
     */
    private val modelixIdToNode = synchronizedMap<Long, SNodeReference>()

    /**
     * The mapping between SModelReferences (SModels) and the corresponding modelix node IDs.
     *
     * The field is the inverse of [modelixIdToModel].
     */
    private val modelToModelixId = synchronizedMap<SModelReference, Long>()

    /**
     * The mapping between the modelix node IDs and the corresponding SModelReferences (SModels).
     *
     * The field is the inverse of [modelToModelixId].
     */
    private val modelixIdToModel = synchronizedMap<Long, SModelReference>()

    /**
     * The mapping between SModuleReferences (SModules) and the corresponding modelix node IDs.
     *
     * The field is the inverse of [modelixIdToModule].
     */
    private val moduleToModelixId = synchronizedMap<SModuleReference, Long>()

    /**
     * The mapping between the modelix node IDs and the corresponding SModuleReferences (SModules).
     *
     * The field is the inverse of [moduleToModelixId].
     */
    private val modelixIdToModule = synchronizedMap<Long, SModuleReference>()

    /**
     * The mapping between the SModule + its outgoing Module Reference (Module Dependencies in MPS) and the
     * corresponding modelix node ID (the node that represents the Module Dependency in modelix).
     *
     * The field is the inverse of [modelixIdToModuleWithOutgoingModuleReference].
     */
    private val moduleWithOutgoingModuleReferenceToModelixId = synchronizedMap<ModuleWithModuleReference, Long>()

    /**
     * The mapping between the modelix node ID (the node that represents the Module Dependency in modelix) and the
     * corresponding SModule + its outgoing Module Reference (Module Dependencies in MPS).
     *
     * The field is the inverse of [moduleWithOutgoingModuleReferenceToModelixId].
     */
    private val modelixIdToModuleWithOutgoingModuleReference = synchronizedMap<Long, ModuleWithModuleReference>()

    /**
     * The mapping between the SModel + its outgoing Module Reference (Language / DevKit Dependency in MPS) and the
     * corresponding modelix node ID (the node that represents the Language / DevKit Dependency in modelix).
     *
     * The field is the inverse of [modelixIdToModelWithOutgoingModuleReference].
     */
    private val modelWithOutgoingModuleReferenceToModelixId = synchronizedMap<ModelWithModuleReference, Long>()

    /**
     * The mapping between the modelix node ID (the node that represents the Language / DevKit Dependency in modelix)
     * and the corresponding SModel + its outgoing Module Reference (Language / DevKit Dependency in MPS).
     *
     * The field is the inverse of [modelWithOutgoingModuleReferenceToModelixId].
     */
    private val modelixIdToModelWithOutgoingModuleReference = synchronizedMap<Long, ModelWithModuleReference>()

    /**
     * The mapping between the SModel + its outgoing Model Reference (Model Import in MPS) and the
     * corresponding modelix node ID (the node that represents the Model Import in modelix).
     *
     * The field is the inverse of [modelixIdToModelWithOutgoingModelReference].
     */
    private val modelWithOutgoingModelReferenceToModelixId = synchronizedMap<ModelWithModelReference, Long>()

    /**
     * The mapping between the modelix node ID (the node that represents the Model Import in modelix) and the
     * corresponding SModel + its outgoing Model Reference (Model Import in MPS).
     *
     * The field is the inverse of [modelWithOutgoingModelReferenceToModelixId].
     */
    private val modelixIdToModelWithOutgoingModelReference = synchronizedMap<Long, ModelWithModelReference>()

    /**
     * For traceability purposes, all items that belong to the SModel (SModelReference) and are stored in the maps above
     * (e.g., SNodes, Model Imports, Language / DevKit Dependencies, etc.) are also stored here. So that, if we remove
     * the SModel from the cache, then we know which items we also have to remove from the other maps.
     */
    private val objectsRelatedToAModel = synchronizedMap<SModelReference, MutableSet<Any>>()

    /**
     * For traceability purposes, all items that belong to the SModule (SModuleReference) and are stored in the maps
     * above (e.g., SModels, Module Dependencies, etc.) are also stored here. So that, if we remove the SModule from the
     * cache, then we know which items we also have to remove from the other maps.
     */
    private val objectsRelatedToAModule = synchronizedMap<SModuleReference, MutableSet<Any>>()

    /**
     * The active [SRepository] to access the [SModel]s and [SModule]s in MPS.
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

    /**
     * Create a mapping from the MPS [node] to the modelix node (identified by [modelixId]).
     *
     * @param node the MPS Node.
     * @param modelixId the identifier of the modelix node.
     */
    fun put(node: SNode, modelixId: Long) {
        val nodeReference = node.reference
        nodeToModelixId[nodeReference] = modelixId
        modelixIdToNode[modelixId] = nodeReference

        node.model?.let { putObjRelatedToAModel(it, nodeReference) }
    }

    /**
     * Create a mapping from the MPS [model] to the modelix node (identified by [modelixId]).
     *
     * @param model the MPS Model.
     * @param modelixId the identifier of the modelix node.
     */
    fun put(model: SModel, modelixId: Long) {
        val modelReference = model.reference
        modelToModelixId[modelReference] = modelixId
        modelixIdToModel[modelixId] = modelReference

        putObjRelatedToAModel(model, modelReference)
    }

    /**
     * Create a mapping from the MPS [module] to the modelix node (identified by [modelixId]).
     *
     * @param module the MPS Module.
     * @param modelixId the identifier of the modelix node.
     */
    fun put(module: SModule, modelixId: Long) {
        val moduleReference = module.moduleReference
        moduleToModelixId[moduleReference] = modelixId
        modelixIdToModule[modelixId] = moduleReference

        putObjRelatedToAModule(module, moduleReference)
    }

    /**
     * Create a mapping from the MPS [sourceModule] and [moduleReference] to the modelix node (identified by
     * [modelixId]).
     *
     * @param sourceModule the MPS Module.
     * @param moduleReference the Module Dependency outgoing from the [sourceModule].
     * @param modelixId the identifier of the modelix node.
     */
    fun put(sourceModule: SModule, moduleReference: SModuleReference, modelixId: Long) {
        val moduleWithOutgoingModuleReference = ModuleWithModuleReference(sourceModule, moduleReference)
        moduleWithOutgoingModuleReferenceToModelixId[moduleWithOutgoingModuleReference] = modelixId
        modelixIdToModuleWithOutgoingModuleReference[modelixId] = moduleWithOutgoingModuleReference

        putObjRelatedToAModule(sourceModule, moduleReference)
    }

    /**
     * Create a mapping from the MPS [sourceModel] and [moduleReference] to the modelix node (identified by
     * [modelixId]).
     *
     * @param sourceModel the MPS Model.
     * @param moduleReference the Language / DevKit Dependency outgoing from the [sourceModel].
     * @param modelixId the identifier of the modelix node.
     */
    fun put(sourceModel: SModel, moduleReference: SModuleReference, modelixId: Long) {
        val modelWithOutgoingModuleReference = ModelWithModuleReference(sourceModel, moduleReference)
        modelWithOutgoingModuleReferenceToModelixId[modelWithOutgoingModuleReference] = modelixId
        modelixIdToModelWithOutgoingModuleReference[modelixId] = modelWithOutgoingModuleReference

        putObjRelatedToAModel(sourceModel, moduleReference)
    }

    /**
     * Create a mapping from the MPS [sourceModel] and [modelReference] to the modelix node (identified by
     * [modelixId]).
     *
     * @param sourceModel the MPS Model.
     * @param modelReference the Model Import outgoing from the [sourceModel].
     * @param modelixId the identifier of the modelix node.
     */
    fun put(sourceModel: SModel, modelReference: SModelReference, modelixId: Long) {
        val modelWithOutgoingModelReference = ModelWithModelReference(sourceModel, modelReference)
        modelWithOutgoingModelReferenceToModelixId[modelWithOutgoingModelReference] = modelixId
        modelixIdToModelWithOutgoingModelReference[modelixId] = modelWithOutgoingModelReference

        putObjRelatedToAModel(sourceModel, modelReference)
    }

    /**
     * Create a traceability link between [model] and the [obj] that is somehow related to the [SModel].
     *
     * @param model the MPS Model.
     * @param obj an object that is related to this Model. E.g., an MPS Node, an [SModelReference], etc.
     */
    private fun putObjRelatedToAModel(model: SModel, obj: Any) {
        val modelReference = model.reference
        objectsRelatedToAModel.computeIfAbsent(modelReference) { synchronizedLinkedHashSet() }.add(obj)
        // just in case, the model has not been tracked yet. E.g. @descriptor models that are created locally but were not synchronized to the model server.
        putObjRelatedToAModule(model.module, modelReference)
    }

    /**
     * Create a traceability link between [module] and the [obj] that is somehow related to the [SModule].
     *
     * @param module the MPS Module.
     * @param obj an object that is related to this Model. E.g., an MPS Node, an [SModuleReference], etc.
     */
    private fun putObjRelatedToAModule(module: SModule, obj: Any) =
        objectsRelatedToAModule.computeIfAbsent(module.moduleReference) { synchronizedLinkedHashSet() }.add(obj)

    /**
     * @param node the MPS Node.
     *
     * @return the modelix node ID that belongs to this MPS Node.
     */
    operator fun get(node: SNode?) = nodeToModelixId[node?.reference]

    /**
     * @param model the MPS Model.
     *
     * @return the modelix node ID that belongs to this MPS Model.
     */
    operator fun get(model: SModel?) = modelToModelixId[model?.reference]

    operator fun get(modelId: SModelId?) =
        modelToModelixId.filter { it.key.modelId == modelId }.map { it.value }.firstOrNull()

    /**
     * @param module the MPS Module.
     *
     * @return the modelix node ID that belongs to this MPS Module.
     */
    operator fun get(module: SModule?) = moduleToModelixId[module?.moduleReference]

    /**
     * @param moduleId the identifier of the MPS Module.
     *
     * @return the modelix node ID that belongs to the MPS Module identified by [moduleId].
     */
    operator fun get(moduleId: SModuleId?) =
        moduleToModelixId.filter { it.key.moduleId == moduleId }.map { it.value }.firstOrNull()

    /**
     * @param sourceModel the MPS Model.
     * @param moduleReference the Language / DevKit Dependency outgoing from the [sourceModel].
     *
     * @return the modelix node ID that represents the [sourceModel] and [moduleReference] together.
     */
    operator fun get(sourceModel: SModel, moduleReference: SModuleReference) =
        modelWithOutgoingModuleReferenceToModelixId[ModelWithModuleReference(sourceModel, moduleReference)]

    /**
     * @param sourceModule the MPS Module.
     * @param moduleReference the Module Dependency outgoing from the [sourceModule].
     *
     * @return the modelix node ID that represents the [sourceModule] and [moduleReference] together.
     */
    operator fun get(sourceModule: SModule, moduleReference: SModuleReference) =
        moduleWithOutgoingModuleReferenceToModelixId[ModuleWithModuleReference(sourceModule, moduleReference)]

    /**
     * @param sourceModel the MPS Model.
     * @param modelReference the Model Import outgoing from the [sourceModel].
     *
     * @return the modelix node ID that represents the [sourceModel] and [modelReference] together.
     */
    operator fun get(sourceModel: SModel, modelReference: SModelReference) =
        modelWithOutgoingModelReferenceToModelixId[ModelWithModelReference(sourceModel, modelReference)]

    /**
     * @param modelixId the modelix node ID that identifies the modelix node.
     *
     * @return the MPS Node that belongs to this modelix node.
     */
    fun getNode(modelixId: Long?): SNode? = modelixIdToNode[modelixId]?.resolve(mpsRepository)

    /**
     * @param modelixId the modelix node ID that identifies the modelix node.
     *
     * @return the MPS Model that belongs to this modelix node.
     */
    fun getModel(modelixId: Long?): SModel? = modelixIdToModel[modelixId]?.resolve(mpsRepository)

    /**
     * @param modelixId the modelix node ID that identifies the modelix node.
     *
     * @return the MPS Module that belongs to this modelix node.
     */
    fun getModule(modelixId: Long?): SModule? = modelixIdToModule[modelixId]?.resolve(mpsRepository)

    /**
     * @param moduleId the identifier of the MPS Module
     *
     * @return the MPS Module that the [moduleId] represents.
     */
    fun getModule(moduleId: SModuleId): SModule? =
        objectsRelatedToAModule.keys.firstOrNull { it.moduleId == moduleId }?.resolve(mpsRepository)

    /**
     * @param modelixId the modelix node ID that identifies the modelix node.
     *
     * @return the [SModel] and the outgoing [SModelReference] (Model Import) that are represented by the modelix node.
     */
    fun getOutgoingModelReference(modelixId: Long?) = modelixIdToModelWithOutgoingModelReference[modelixId]

    /**
     * @param modelixId the modelix node ID that identifies the modelix node.
     *
     * @return the [SModel] and the outgoing [SModuleReference] (Language / DevKit Dependency) that are represented by
     * the modelix node.
     */
    fun getOutgoingModuleReferenceFromModel(modelixId: Long?) = modelixIdToModelWithOutgoingModuleReference[modelixId]

    /**
     * @param modelixId the modelix node ID that identifies the modelix node.
     *
     * @return the [SModule] and the outgoing [SModuleReference] (Module Dependency) that is represented by the modelix
     * node.
     */
    fun getOutgoingModuleReferenceFromModule(modelixId: Long?) = modelixIdToModuleWithOutgoingModuleReference[modelixId]

    /**
     * Deletes every item from the cache that is related to the modelix node represented by its ID ([modelixId]).
     *
     * @param modelixId the ID of the modelix node.
     */
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

    /**
     * Deletes every item from the cache that is related to the [model].
     *
     * @param model the MPS Model.
     */
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

    /**
     * Deletes every item from the cache that is related to the [module].
     *
     * @param module the MPS Module.
     */
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

    /**
     * Deletes every item from the cache that is related to the [outgoingModelReference].
     *
     * @param outgoingModelReference the [SModel] and its outgoing [SModelReference] (Model Import).
     */
    fun remove(outgoingModelReference: ModelWithModelReference) {
        modelWithOutgoingModelReferenceToModelixId.remove(outgoingModelReference)
            ?.let { id -> modelixIdToModelWithOutgoingModelReference.remove(id) }
    }

    /**
     * Deletes every item from the cache that is related to the [modelWithModuleReference].
     *
     * @param modelWithModuleReference the [SModel] and its outgoing [SModuleReference] (Language / DevKit Dependency).
     */
    fun remove(modelWithModuleReference: ModelWithModuleReference) {
        modelWithOutgoingModuleReferenceToModelixId.remove(modelWithModuleReference)
            ?.let { id -> modelixIdToModelWithOutgoingModuleReference.remove(id) }
    }

    /**
     * Deletes every item from the cache that is related to the [moduleWithModuleReference].
     *
     * @param moduleWithModuleReference the [SModule] and its outgoing [SModuleReference] (Module Dependency).
     */
    fun remove(moduleWithModuleReference: ModuleWithModuleReference) {
        moduleWithOutgoingModuleReferenceToModelixId.remove(moduleWithModuleReference)
            ?.let { id -> modelixIdToModuleWithOutgoingModuleReference.remove(id) }
    }

    /**
     * @param modelixId the ID of the modelix node.
     *
     * @return true if [modelixId] is mapped to any MPS element in the cache.
     */
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

    /**
     * @param model the MPS Model.
     *
     * @return true if the [model] is mapped to a modelix node. I.e., the modelix node ID is available in the cache.
     */
    fun isMappedToModelix(model: SModel) = this[model] != null

    /**
     * @param module the MPS Module.
     *
     * @return true if the [module] is mapped to a modelix node. I.e., the modelix node ID is available in the cache.
     */
    fun isMappedToModelix(module: SModule) = this[module] != null

    /**
     * @param node the MPS Module.
     *
     * @return true if the [node] is mapped to a modelix node. I.e., the modelix node ID is available in the cache.
     */
    fun isMappedToModelix(node: SNode) = this[node] != null

    /**
     * Clears all sub-caches.
     */
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

/**
 * Represents an MPS Model with its outgoing Model Import.
 *
 * @property sourceModelReference an [SModelReference] for the [SModel] that contains the Model Import.
 * @property modelReference the [SModelReference] that represents the Model Import.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class ModelWithModelReference(
    val sourceModelReference: SModelReference,
    val modelReference: SModelReference,
) {
    /**
     * A helper constructor that uses the source [SModel] instead of its [SModelReference].
     *
     * @param source the [SModel] that contains the Model Import.
     * @param modelReference the [SModelReference] that represents the Model Import.
     */
    constructor(source: SModel, modelReference: SModelReference) : this(source.reference, modelReference)
}

/**
 * Represents an MPS Model with its outgoing Language / DevKit Dependency.
 *
 * @property sourceModelReference an [SModelReference] for the [SModel] that contains the Language / DevKit Dependency.
 * @property moduleReference the [SModuleReference] that represents the Language / DevKit Dependency.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class ModelWithModuleReference(
    val sourceModelReference: SModelReference,
    val moduleReference: SModuleReference,
) {
    /**
     * A helper constructor that uses the source [SModel] instead of its [SModelReference].
     *
     * @param source the [SModel] that contains the Language / DevKit Dependency.
     * @param moduleReference the [SModuleReference] that represents the Language / DevKit Dependency.
     */
    constructor(source: SModel, moduleReference: SModuleReference) : this(source.reference, moduleReference)
}

/**
 * Represents an MPS Module with its outgoing Module Dependency.
 *
 * @property sourceModuleReference an [SModuleReference] for the [SModule] that contains the Module Dependency.
 * @property moduleReference the [SModuleReference] that represents the Module Dependency.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
data class ModuleWithModuleReference(
    val sourceModuleReference: SModuleReference,
    val moduleReference: SModuleReference,
) {
    /**
     * A helper constructor that uses the source [SModule] instead of its [SModuleReference].
     *
     * @param source the [SModule] that contains the Module Dependency.
     * @param moduleReference the [SModuleReference] that represents the Module Dependency.
     */
    constructor(source: SModule, moduleReference: SModuleReference) : this(source.moduleReference, moduleReference)
}
