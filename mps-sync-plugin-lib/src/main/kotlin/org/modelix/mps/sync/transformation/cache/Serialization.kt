package org.modelix.mps.sync.transformation.cache

import jetbrains.mps.extapi.model.SModelBase
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.mps.util.getModelixId

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal class SModuleSerializer(private val repository: SRepository) : KSerializer<SModule> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(SModule::class.simpleName!!) {
        element<String>("id")
    }

    override fun serialize(encoder: Encoder, value: SModule) = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.getModelixId())
    }

    override fun deserialize(decoder: Decoder): SModule {
        return decoder.decodeStructure(descriptor) {
            var id = ""
            loop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    DECODE_DONE -> break@loop
                    0 -> id = decodeStringElement(descriptor, 0)
                    else -> throw SerializationException("Unexpected index $index")
                }
            }

            val moduleId = PersistenceFacade.getInstance().createModuleId(id)
            val module = repository.getModule(moduleId)
            requireNotNull(module) { "Module with ID '$moduleId' is not found." }
            module
        }
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal class SModelSerializer(repository: SRepository) : KSerializer<SModel> {

    private val parentModuleSerializer = SModuleSerializer(repository)

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(SModel::class.simpleName!!) {
        element<String>("id")
        element<SModule>("parentModule")
    }

    override fun serialize(encoder: Encoder, value: SModel) = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.getModelixId())
        encodeSerializableElement(descriptor, 1, parentModuleSerializer, value.module)
    }

    override fun deserialize(decoder: Decoder): SModel {
        return decoder.decodeStructure(descriptor) {
            var id = ""
            lateinit var parentModule: SModule
            loop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    DECODE_DONE -> break@loop
                    0 -> id = decodeStringElement(descriptor, 0)
                    1 -> parentModule = decodeSerializableElement(descriptor, 1, parentModuleSerializer)
                    else -> throw SerializationException("Unexpected index $index")
                }
            }

            val modelId = PersistenceFacade.getInstance().createModelId(id)
            val model = parentModule.getModel(modelId)
            requireNotNull(model) { "Model with ID '$modelId' is not found in Module '${parentModule.moduleName}'." }
            model
        }
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal class SNodeSerializer(repository: SRepository) : KSerializer<SNode> {

    private val parentModelSerializer = SModelSerializer(repository)

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(SNode::class.simpleName!!) {
        element<String>("id")
        element<SModel>("parentModel")
    }

    override fun serialize(encoder: Encoder, value: SNode) = encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, value.getModelixId())

        requireNotNull(value.model) { "Node '${value.name}''s Model is null." }
        encodeSerializableElement(descriptor, 1, parentModelSerializer, value.model!!)
    }

    override fun deserialize(decoder: Decoder): SNode {
        return decoder.decodeStructure(descriptor) {
            var id = ""
            lateinit var parentModel: SModel
            loop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    DECODE_DONE -> break@loop
                    0 -> id = decodeStringElement(descriptor, 0)
                    1 -> parentModel = decodeSerializableElement(descriptor, 1, parentModelSerializer)
                    else -> throw SerializationException("Unexpected index $index")
                }
            }

            val nodeId = PersistenceFacade.getInstance().createNodeId(id)
            val node = parentModel.getNode(nodeId)
            requireNotNull(node) { "Node with ID '$nodeId' is not found in Model '${parentModel.name}' (parent Module: ${parentModel.module?.moduleName})." }
            node
        }
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal class SModelReferenceSerializer(private val repository: SRepository) : KSerializer<SModelReference> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(SModelReference::class.simpleName!!) {
        element<String>("modelId")
        element<String>("parentModuleId")
    }

    override fun serialize(encoder: Encoder, value: SModelReference) = encoder.encodeStructure(descriptor) {
        val model = value.resolve(repository)

        encodeStringElement(descriptor, 0, model.getModelixId())
        encodeStringElement(descriptor, 1, model.module.getModelixId())
    }

    override fun deserialize(decoder: Decoder): SModelReference {
        return decoder.decodeStructure(descriptor) {
            var modelId = ""
            var parentModuleId = ""
            loop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    DECODE_DONE -> break@loop
                    0 -> modelId = decodeStringElement(descriptor, 0)
                    1 -> parentModuleId = decodeStringElement(descriptor, 0)
                    else -> throw SerializationException("Unexpected index $index")
                }
            }

            val moduleId = PersistenceFacade.getInstance().createModuleId(parentModuleId)
            val parentModule = repository.getModule(moduleId)
            requireNotNull(parentModule) { "Module with ID '$moduleId' is not found." }

            val mpsModelId = PersistenceFacade.getInstance().createModelId(modelId)
            val model = parentModule.getModel(mpsModelId)
            requireNotNull(model) { "Model with ID '$mpsModelId' is not found in Module '${parentModule.moduleName}'." }
            model.reference
        }
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal class SModuleReferenceSerializer(private val repository: SRepository) : KSerializer<SModuleReference> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(SModuleReference::class.simpleName!!) {
        element<String>("moduleId")
    }

    override fun serialize(encoder: Encoder, value: SModuleReference) = encoder.encodeStructure(descriptor) {
        val module = value.resolve(repository)
        requireNotNull(module) { "Cannot serialize ModuleReference '$value', because resolved Module is null." }
        encodeStringElement(descriptor, 0, module.getModelixId())
    }

    override fun deserialize(decoder: Decoder): SModuleReference {
        return decoder.decodeStructure(descriptor) {
            var id = ""
            loop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    DECODE_DONE -> break@loop
                    0 -> id = decodeStringElement(descriptor, 0)
                    else -> throw SerializationException("Unexpected index $index")
                }
            }

            val moduleId = PersistenceFacade.getInstance().createModuleId(id)
            val module = repository.getModule(moduleId)
            requireNotNull(module) { "Module with ID '$moduleId' is not found." }
            module.moduleReference
        }
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal class ModelWithModelReferenceSerializer(repository: SRepository) : KSerializer<ModelWithModelReference> {

    private val modelSerializer = SModelSerializer(repository)
    private val modelReferenceSerializer = SModelReferenceSerializer(repository)

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(ModelWithModelReference::class.simpleName!!) {
            element<SModel>("sourceModel")
            element<SModelReference>("targetModelReference")
        }

    override fun serialize(encoder: Encoder, value: ModelWithModelReference) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, modelSerializer, value.source)
        encodeSerializableElement(descriptor, 1, modelReferenceSerializer, value.modelReference)
    }

    override fun deserialize(decoder: Decoder): ModelWithModelReference {
        return decoder.decodeStructure(descriptor) {
            lateinit var model: SModel
            lateinit var modelReference: SModelReference
            loop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    DECODE_DONE -> break@loop
                    0 -> model = decodeSerializableElement(descriptor, 0, modelSerializer)
                    1 -> modelReference = decodeSerializableElement(descriptor, 1, modelReferenceSerializer)
                    else -> throw SerializationException("Unexpected index $index")
                }
            }
            ModelWithModelReference(model, modelReference)
        }
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal class ModelWithModuleReferenceSerializer(repository: SRepository) : KSerializer<ModelWithModuleReference> {

    private val modelSerializer = SModelSerializer(repository)
    private val moduleReferenceSerializer = SModuleReferenceSerializer(repository)

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(ModelWithModuleReference::class.simpleName!!) {
            element<SModel>("sourceModel")
            element<SModuleReference>("targetModuleReference")
        }

    override fun serialize(encoder: Encoder, value: ModelWithModuleReference) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, modelSerializer, value.source)
        encodeSerializableElement(descriptor, 1, moduleReferenceSerializer, value.moduleReference)
    }

    override fun deserialize(decoder: Decoder): ModelWithModuleReference {
        return decoder.decodeStructure(descriptor) {
            lateinit var model: SModel
            lateinit var deserializedModuleReference: SModuleReference
            loop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    DECODE_DONE -> break@loop
                    0 -> model = decodeSerializableElement(descriptor, 0, modelSerializer)
                    1 ->
                        deserializedModuleReference =
                            decodeSerializableElement(descriptor, 1, moduleReferenceSerializer)

                    else -> throw SerializationException("Unexpected index $index")
                }
            }

            require(model is SModelBase) { "Deserialized Model '${model.name}' (parent Module: ${model.module?.moduleName}) is not an SModelBase." }

            /*
             * We have to find the devkit or language dependency that corresponds to the module reference. Otherwise, we
             * would return a different moduleReference object than what is inside the model, which will break the
             * synchronization mapping.
             */
            var moduleReference =
                model.importedDevkits().firstOrNull { it.moduleId == deserializedModuleReference.moduleId }
            if (moduleReference == null) {
                moduleReference = model.importedLanguageIds()
                    .firstOrNull { it.sourceModuleReference.moduleId == deserializedModuleReference.moduleId }
                    ?.sourceModuleReference
            }
            requireNotNull(moduleReference) { "ModuleReference '$deserializedModuleReference' was not found as an outgoing reference from the Model '${model.name}' (parent Module: ${model.module?.moduleName})." }

            ModelWithModuleReference(model, moduleReference)
        }
    }
}

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal class ModuleWithModuleReferenceSerializer(repository: SRepository) : KSerializer<ModuleWithModuleReference> {

    private val moduleSerializer = SModuleSerializer(repository)
    private val moduleReferenceSerializer = SModuleReferenceSerializer(repository)

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(ModuleWithModuleReference::class.simpleName!!) {
            element<SModel>("sourceModule")
            element<SModuleReference>("targetModuleReference")
        }

    override fun serialize(encoder: Encoder, value: ModuleWithModuleReference) = encoder.encodeStructure(descriptor) {
        encodeSerializableElement(descriptor, 0, moduleSerializer, value.source)
        encodeSerializableElement(descriptor, 1, moduleReferenceSerializer, value.moduleReference)
    }

    override fun deserialize(decoder: Decoder): ModuleWithModuleReference {
        return decoder.decodeStructure(descriptor) {
            lateinit var module: SModule
            lateinit var deserializedModuleReference: SModuleReference
            loop@ while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    DECODE_DONE -> break@loop
                    0 -> module = decodeSerializableElement(descriptor, 0, moduleSerializer)
                    1 ->
                        deserializedModuleReference =
                            decodeSerializableElement(descriptor, 1, moduleReferenceSerializer)

                    else -> throw SerializationException("Unexpected index $index")
                }
            }

            /*
             * We have to find the module dependency that corresponds to the module reference. Otherwise, we
             * would return a different moduleReference object than what is inside the module, which will break the
             * synchronization mapping.
             */
            val moduleReference = module.declaredDependencies
                .firstOrNull { it.targetModule.moduleId == deserializedModuleReference.moduleId }?.targetModule
            requireNotNull(moduleReference) { "ModuleReference '$deserializedModuleReference' was not found as an outgoing reference from the Module '${module.moduleName}'." }

            ModuleWithModuleReference(module, moduleReference)
        }
    }
}
