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
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.util.synchronizedLinkedHashSet
import org.modelix.mps.sync.util.synchronizedMap

/**
 * WARNING:
 * - use with caution, otherwise this cache may cause memory leaks
 * - if you add a new Map as a field in the class, then please also add it to the `remove`, `isMappedToMps`, and `isMappedToModelix` methods below
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object MpsToModelixMap {

    private val nodeToModelixId = synchronizedMap<SNode, Long>()
    private val modelixIdToNode = synchronizedMap<Long, SNode>()

    private val modelToModelixId = synchronizedMap<SModel, Long>()
    private val modelixIdToModel = synchronizedMap<Long, SModel>()

    private val moduleToModelixId = synchronizedMap<SModule, Long>()
    private val modelixIdToModule = synchronizedMap<Long, SModule>()

    private val moduleWithOutgoingModuleReferenceToModelixId = synchronizedMap<ModuleWithModuleReference, Long>()
    private val modelixIdToModuleWithOutgoingModuleReference = synchronizedMap<Long, ModuleWithModuleReference>()

    private val modelWithOutgoingModuleReferenceToModelixId = synchronizedMap<ModelWithModuleReference, Long>()
    private val modelixIdToModelWithOutgoingModuleReference = synchronizedMap<Long, ModelWithModuleReference>()

    private val modelWithOutgoingModelReferenceToModelixId = synchronizedMap<ModelWithModelReference, Long>()
    private val modelixIdToModelWithOutgoingModelReference = synchronizedMap<Long, ModelWithModelReference>()

    private val objectsRelatedToAModel = synchronizedMap<SModel, MutableSet<Any>>()
    private val objectsRelatedToAModule = synchronizedMap<SModule, MutableSet<Any>>()

    fun put(node: SNode, modelixId: Long) {
        nodeToModelixId[node] = modelixId
        modelixIdToNode[modelixId] = node

        node.model?.let { putObjRelatedToAModel(it, node) }
    }

    fun put(model: SModel, modelixId: Long) {
        modelToModelixId[model] = modelixId
        modelixIdToModel[modelixId] = model

        putObjRelatedToAModel(model, model)
    }

    fun put(module: SModule, modelixId: Long) {
        moduleToModelixId[module] = modelixId
        modelixIdToModule[modelixId] = module

        putObjRelatedToAModule(module, module)
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

    private fun putObjRelatedToAModel(model: SModel, obj: Any?) {
        objectsRelatedToAModel.computeIfAbsent(model) { synchronizedLinkedHashSet() }.add(obj!!)
        // just in case, the model has not been tracked yet. E.g. @descriptor models that are created locally but were not synchronized to the model server.
        putObjRelatedToAModule(model.module, model)
    }

    private fun putObjRelatedToAModule(module: SModule, obj: Any?) =
        objectsRelatedToAModule.computeIfAbsent(module) { synchronizedLinkedHashSet() }.add(obj!!)

    operator fun get(node: SNode?) = nodeToModelixId[node]

    operator fun get(model: SModel?) = modelToModelixId[model]

    operator fun get(modelId: SModelId?) =
        modelToModelixId.filter { it.key.modelId == modelId }.map { it.value }.firstOrNull()

    operator fun get(module: SModule?) = moduleToModelixId[module]

    operator fun get(moduleId: SModuleId?) =
        moduleToModelixId.filter { it.key.moduleId == moduleId }.map { it.value }.firstOrNull()

    operator fun get(sourceModel: SModel, moduleReference: SModuleReference) =
        modelWithOutgoingModuleReferenceToModelixId[ModelWithModuleReference(sourceModel, moduleReference)]

    operator fun get(sourceModule: SModule, moduleReference: SModuleReference) =
        moduleWithOutgoingModuleReferenceToModelixId[ModuleWithModuleReference(sourceModule, moduleReference)]

    operator fun get(sourceModel: SModel, modelReference: SModelReference) =
        modelWithOutgoingModelReferenceToModelixId[ModelWithModelReference(sourceModel, modelReference)]

    fun getNode(modelixId: Long?) = modelixIdToNode[modelixId]

    fun getModel(modelixId: Long?) = modelixIdToModel[modelixId]

    fun getModule(modelixId: Long?) = modelixIdToModule[modelixId]

    fun getModule(moduleId: SModuleId) = objectsRelatedToAModule.keys.firstOrNull { it.moduleId == moduleId }

    fun getOutgoingModelReference(modelixId: Long?) = modelixIdToModelWithOutgoingModelReference[modelixId]

    fun getOutgoingModuleReferenceFromModel(modelixId: Long?) = modelixIdToModelWithOutgoingModuleReference[modelixId]

    fun getOutgoingModuleReferenceFromModule(modelixId: Long?) = modelixIdToModuleWithOutgoingModuleReference[modelixId]

    fun remove(modelixId: Long) {
        // is related to node
        modelixIdToNode.remove(modelixId)?.let { nodeToModelixId.remove(it) }

        // is related to model
        modelixIdToModel.remove(modelixId)?.let {
            modelToModelixId.remove(it)
            remove(it)
        }
        modelixIdToModelWithOutgoingModelReference.remove(modelixId)
            ?.let { modelWithOutgoingModelReferenceToModelixId.remove(it) }
        modelixIdToModelWithOutgoingModuleReference.remove(modelixId)
            ?.let { modelWithOutgoingModuleReferenceToModelixId.remove(it) }

        // is related to module
        modelixIdToModule.remove(modelixId)?.let { remove(it) }
        modelixIdToModuleWithOutgoingModuleReference.remove(modelixId)
            ?.let { moduleWithOutgoingModuleReferenceToModelixId.remove(it) }
    }

    fun remove(model: SModel) {
        modelToModelixId.remove(model)?.let { modelixIdToModel.remove(it) }
        objectsRelatedToAModel.remove(model)?.forEach {
            when (it) {
                is SModuleReference -> {
                    val target = ModelWithModuleReference(model, it)
                    modelWithOutgoingModuleReferenceToModelixId.remove(target)
                        ?.let { id -> modelixIdToModelWithOutgoingModuleReference.remove(id) }
                }

                is SModelReference -> {
                    val target = ModelWithModelReference(model, it)
                    modelWithOutgoingModelReferenceToModelixId.remove(target)
                        ?.let { id -> modelixIdToModelWithOutgoingModelReference.remove(id) }
                }

                is SNode -> {
                    nodeToModelixId.remove(it)?.let { modelixId -> modelixIdToNode.remove(modelixId) }
                }
            }
        }
    }

    fun remove(module: SModule) {
        moduleToModelixId.remove(module)?.let { modelixIdToModule.remove(it) }
        objectsRelatedToAModule.remove(module)?.forEach {
            if (it is SModuleReference) {
                val target = ModuleWithModuleReference(module, it)
                moduleWithOutgoingModuleReferenceToModelixId.remove(target)
                    ?.let { id -> modelixIdToModuleWithOutgoingModuleReference.remove(id) }
            } else if (it is SModel) {
                remove(it)
            }
        }
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

    class Serializer {

        companion object {
            private const val MPS_TO_MODELIX_MAP_PREFIX = "MpsToModelixMap: "
            private const val NODE_TO_MODELIX_ID_PREFIX = "nodeToModelixId: "
            private const val MODEL_TO_MODELIX_ID_PREFIX = "modelToModelixId: "
            private const val MODULE_TO_MODELIX_ID_PREFIX = "moduleToModelixId: "
            private const val MODULE_WITH_OUTGOING_MODULE_REFERENCE_TO_MODELIX_ID_PREFIX =
                "moduleWithOutgoingModuleReferenceToModelixId: "
            private const val MODEL_WITH_OUTGOING_MODULE_REFERENCE_TO_MODELIX_ID_PREFIX =
                "modelWithOutgoingModuleReferenceToModelixId: "
            private const val MODEL_WITH_OUTGOING_MODEL_REFERENCE_TO_MODELIX_ID_PREFIX =
                "modelWithOutgoingModelReferenceToModelixId: "

            private const val MODELIX_ID_SEPARATOR = "modelixId"
            private const val SEPARATOR_INSIDE_RECORDS = ", $MODELIX_ID_SEPARATOR: "
            private const val RECORD_PREFIX = "["
            private const val RECORD_SUFFIX = "]"
            private const val SEPARATOR_BETWEEN_RECORDS = " % "

            private const val FIELD_PREFIX = "["
            private const val FIELD_SUFFIX = "]"
            private const val SEPARATOR_BETWEEN_FIELDS = "; "

            private const val MAP_PREFIX = FIELD_PREFIX + MPS_TO_MODELIX_MAP_PREFIX
            private const val MAP_SUFFIX = "]"
        }

        private val repository
            get() = ActiveMpsProjectInjector.activeMpsProject?.repository

        private val nodeSerializer
            get() = SNodeSerializer(repository)

        private val modelSerializer
            get() = SModelSerializer(repository)

        private val moduleSerializer
            get() = SModuleSerializer(repository)

        private val moduleWithModuleReferenceSerializer
            get() = ModuleWithModuleReferenceSerializer(repository)

        private val modelWithModuleReferenceSerializer
            get() = ModelWithModuleReferenceSerializer(repository)

        private val modelWithModelReferenceSerializer
            get() = ModelWithModelReferenceSerializer(repository)

        fun serialize(): String {
            /**
             * serialized structure:
             * [MpsToModelixMap: [nodeToModelixId: [[Node], modelixId: 42], [[Node], modelixId: 43]], ..., [modelWithOutgoingModuleReferenceToModelixId: [[ModuleWithModuleReference], modelixId: 44], [[ModuleWithModuleReference], modelixId: 45]]
             */

            val sb = StringBuilder(MAP_PREFIX)
            serialize(nodeToModelixId, NODE_TO_MODELIX_ID_PREFIX, sb, nodeSerializer)
            serialize(modelToModelixId, MODEL_TO_MODELIX_ID_PREFIX, sb, modelSerializer)
            serialize(moduleToModelixId, MODULE_TO_MODELIX_ID_PREFIX, sb, moduleSerializer)
            serialize(
                moduleWithOutgoingModuleReferenceToModelixId,
                MODULE_WITH_OUTGOING_MODULE_REFERENCE_TO_MODELIX_ID_PREFIX,
                sb,
                moduleWithModuleReferenceSerializer,
            )
            serialize(
                modelWithOutgoingModuleReferenceToModelixId,
                MODEL_WITH_OUTGOING_MODULE_REFERENCE_TO_MODELIX_ID_PREFIX,
                sb,
                modelWithModuleReferenceSerializer,
            )
            serialize(
                modelWithOutgoingModelReferenceToModelixId,
                MODEL_WITH_OUTGOING_MODEL_REFERENCE_TO_MODELIX_ID_PREFIX,
                sb,
                modelWithModelReferenceSerializer,
            )
            sb.setLength(sb.length - SEPARATOR_BETWEEN_FIELDS.length)

            sb.append(MAP_SUFFIX)
            return sb.toString()
        }

        fun deserialize(from: String): MpsToModelixMap {
            val rest = from.removePrefix(MAP_PREFIX).removeSuffix(MAP_SUFFIX)
            val split = rest.split(SEPARATOR_BETWEEN_FIELDS)
            require(split.size == 6) { "After splitting string ($from) by separator, it must consist of 6 parts, but it consist of ${split.size} parts." }

            // 1. deserialize all values locally
            val nodeToModelixIdLocal = mutableMapOf<SNode, Long>()
            deserialize(split[0], NODE_TO_MODELIX_ID_PREFIX, nodeToModelixIdLocal, nodeSerializer)

            val modelToModelixIdLocal = mutableMapOf<SModel, Long>()
            deserialize(split[1], MODEL_TO_MODELIX_ID_PREFIX, modelToModelixIdLocal, modelSerializer)

            val moduleToModelixIdLocal = mutableMapOf<SModule, Long>()
            deserialize(split[2], MODULE_TO_MODELIX_ID_PREFIX, moduleToModelixIdLocal, moduleSerializer)

            val moduleWithOutgoingModuleReferenceToModelixIdLocal = mutableMapOf<ModuleWithModuleReference, Long>()
            deserialize(
                split[3],
                MODULE_WITH_OUTGOING_MODULE_REFERENCE_TO_MODELIX_ID_PREFIX,
                moduleWithOutgoingModuleReferenceToModelixIdLocal,
                moduleWithModuleReferenceSerializer,
            )

            val modelWithOutgoingModuleReferenceToModelixIdLocal = mutableMapOf<ModelWithModuleReference, Long>()
            deserialize(
                split[4],
                MODEL_WITH_OUTGOING_MODULE_REFERENCE_TO_MODELIX_ID_PREFIX,
                modelWithOutgoingModuleReferenceToModelixIdLocal,
                modelWithModuleReferenceSerializer,
            )

            val modelWithOutgoingModelReferenceToModelixIdLocal = mutableMapOf<ModelWithModelReference, Long>()
            deserialize(
                split[5],
                MODEL_WITH_OUTGOING_MODEL_REFERENCE_TO_MODELIX_ID_PREFIX,
                modelWithOutgoingModelReferenceToModelixIdLocal,
                modelWithModelReferenceSerializer,
            )

            // 2. load these values into the map
            nodeToModelixIdLocal.forEach { put(it.key, it.value) }
            modelToModelixIdLocal.forEach { put(it.key, it.value) }
            moduleToModelixIdLocal.forEach { put(it.key, it.value) }
            moduleWithOutgoingModuleReferenceToModelixIdLocal.forEach {
                put(
                    it.key.source,
                    it.key.moduleReference,
                    it.value,
                )
            }
            modelWithOutgoingModuleReferenceToModelixIdLocal.forEach {
                put(
                    it.key.source,
                    it.key.moduleReference,
                    it.value,
                )
            }
            modelWithOutgoingModelReferenceToModelixIdLocal.forEach {
                put(
                    it.key.source,
                    it.key.modelReference,
                    it.value,
                )
            }

            return MpsToModelixMap
        }

        private fun <T> serialize(
            values: Map<T, Long>,
            prefix: String,
            sb: StringBuilder,
            serializer: org.modelix.mps.sync.transformation.cache.Serializer<T>,
        ) {
            sb.append(FIELD_PREFIX)
            sb.append(prefix)
            values.entries.forEach {
                sb.append(RECORD_PREFIX)
                sb.append(serializer.serialize(it.key))
                sb.append(SEPARATOR_INSIDE_RECORDS)
                sb.append(it.value)
                sb.append(RECORD_SUFFIX)
                sb.append(SEPARATOR_BETWEEN_RECORDS)
            }
            if (values.entries.isNotEmpty()) {
                // remove last character
                sb.setLength(sb.length - SEPARATOR_BETWEEN_RECORDS.length)
            }

            sb.append(FIELD_SUFFIX)
            sb.append(SEPARATOR_BETWEEN_FIELDS)
        }

        private fun <T> deserialize(
            from: String,
            prefix: String,
            resultCollector: MutableMap<T, Long>,
            deserializer: org.modelix.mps.sync.transformation.cache.Serializer<T>,
        ) {
            val cleaned = from.removePrefix(FIELD_PREFIX + prefix).removeSuffix(FIELD_SUFFIX)
            if (cleaned.isBlank()) {
                return
            }

            cleaned.split(SEPARATOR_BETWEEN_RECORDS).forEach {
                val insideValues = it.split(SEPARATOR_INSIDE_RECORDS)
                require(insideValues.size == 2) { "There must be 2 parts after splitting inside values. But it has ${insideValues.size} parts. See string: $it" }

                val serialized = insideValues[0].removePrefix(RECORD_PREFIX)
                val deserialized = deserializer.deserializeNotNull(serialized)

                val modelixId = insideValues[1].removeSuffix(RECORD_SUFFIX).toLong()
                resultCollector[deserialized] = modelixId
            }
        }
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class ModelWithModelReference(val source: SModel, val modelReference: SModelReference)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class ModelWithModuleReference(val source: SModel, val moduleReference: SModuleReference)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class ModuleWithModuleReference(val source: SModule, val moduleReference: SModuleReference)
