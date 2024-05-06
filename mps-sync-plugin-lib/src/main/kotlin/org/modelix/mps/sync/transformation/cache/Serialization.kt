package org.modelix.mps.sync.transformation.cache

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.mps.util.clone
import org.modelix.mps.sync.mps.util.getModelixId

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
abstract class Serializer<T> {

    protected abstract val typeDelimiter: String

    protected open val infixSeparator: String? = null

    private val serializationPrefix
        get() = "[$typeDelimiter: "
    private val serializationSuffix = "]"

    abstract fun serialize(it: T): String
    abstract fun deserialize(from: String): T?

    fun serialize(value: String) = "$serializationPrefix$value$serializationSuffix"

    fun deserializeNotNull(from: String): T {
        val deserialized = deserialize(from)
        requireNotNull(deserialized) { "$typeDelimiter deserialized from '$from' is null." }
        return deserialized
    }

    // serialization
    protected fun <U> serializeWithRhs(serializedLhs: String, lhs: T, rhs: U?, rhsSerializer: Serializer<U>): String {
        requireNotNull(rhs) { "$typeDelimiter ($lhs)'s right-hand-side value is null." }
        val serializedRhs = rhsSerializer.serialize(rhs)
        return serialize("$serializedLhs$infixSeparator$serializedRhs")
    }

    // deserialization
    protected fun extractValue(from: String) = from.replace(serializationPrefix, "").replace(serializationSuffix, "")

    protected fun splitIntoLhsAndRhs(from: String): List<String> {
        requireNotNull(infixSeparator) { "Infix separator cannot be null." }
        val serializedValue = extractValue(from)
        val split = serializedValue.split(infixSeparator!!)
        require(split.size == 2) { "Serialized value ($from) cannot be split into two parts via its infix separator ($infixSeparator)." }
        return split
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal class SModuleSerializer(private val repository: SRepository? = null) : Serializer<SModule>() {

    override val typeDelimiter = "SModule"

    // [SModule: ID]
    override fun serialize(it: SModule) = this.serialize(it.getModelixId())

    override fun deserialize(from: String): SModule? {
        requireNotNull(repository) { "Cannot deserialize, if repository is null." }

        val id = extractValue(from)
        val moduleId = PersistenceFacade.getInstance().createModuleId(id)
        return repository.getModule(moduleId)
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal class SModelSerializer(repository: SRepository? = null) : Serializer<SModel>() {

    override val typeDelimiter = "SModel"
    override val infixSeparator = ", parentModule: "

    private val parentSerializer = SModuleSerializer(repository)

    // [SModel: ID, parentModule: [see SModuleSerializer.serialize(module)]]
    override fun serialize(it: SModel) = serializeWithRhs(it.getModelixId(), it, it.module, parentSerializer)

    override fun deserialize(from: String): SModel? {
        val split = splitIntoLhsAndRhs(from)

        val parentId = split[1]
        val parent = parentSerializer.deserializeNotNull(parentId)

        val modelId = PersistenceFacade.getInstance().createModelId(split[0])
        return parent.getModel(modelId)
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal class SNodeSerializer(repository: SRepository? = null) : Serializer<SNode>() {

    override val typeDelimiter = "SNode"
    override val infixSeparator = ", parentModel: "

    private val parentSerializer = SModelSerializer(repository)

    // [SModel: ID, parentModel: [see SModelSerializer.serialize(model)]
    override fun serialize(it: SNode) = serializeWithRhs(it.getModelixId(), it, it.model, parentSerializer)

    override fun deserialize(from: String): SNode? {
        val split = splitIntoLhsAndRhs(from)

        val parentId = split[1]
        val parent = parentSerializer.deserializeNotNull(parentId)

        val nodeId = PersistenceFacade.getInstance().createNodeId(split[0])
        return parent.getNode(nodeId)
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal class SModelReferenceSerializer(private val repository: SRepository? = null) : Serializer<SModelReference>() {

    override val typeDelimiter = "SModelReference"

    private val modelSerializer = SModelSerializer(repository)

    // [SModelReference: [see SModelSerializer.serialize(model)]
    override fun serialize(it: SModelReference): String {
        requireNotNull(repository) { "Cannot serialize, if repository is null." }
        val model = it.resolve(repository)
        val serializedModel = modelSerializer.serialize(model)
        return serialize(serializedModel)
    }

    // TODO maybe we have to deserialize it directly in the model, where it was used. Otherwise the ModelChangeListener will not work correctly for model imports
    override fun deserialize(from: String): SModelReference? {
        val serializedModel = extractValue(from)
        val model = modelSerializer.deserialize(serializedModel)
        return model?.reference?.clone()
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal class SModuleReferenceSerializer(private val repository: SRepository? = null) :
    Serializer<SModuleReference>() {

    override val typeDelimiter = "SModuleReference"

    private val moduleSerializer = SModuleSerializer(repository)

    // [SModuleReference: [see SModuleSerializer.serialize(module)]
    override fun serialize(it: SModuleReference): String {
        requireNotNull(repository) { "Cannot serialize, if repository is null." }
        val module = it.resolve(repository)
        requireNotNull(module) { "Cannot serialize module reference ($it), because resolved Module is null." }
        val serializedModule = moduleSerializer.serialize(module)
        return serialize(serializedModule)
    }

    // TODO maybe we have to deserialize it directly in the model, where it was used. Otherwise the ModelChangeListener will not work correctly for devkit/language/module dependencies
    override fun deserialize(from: String): SModuleReference? {
        val serializedModule = extractValue(from)
        val module = moduleSerializer.deserialize(serializedModule)
        return module?.moduleReference?.clone()
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal class ModelWithModelReferenceSerializer(repository: SRepository? = null) :
    Serializer<ModelWithModelReference>() {

    override val typeDelimiter = "ModelWithModelReference"
    override val infixSeparator = ", modelReference: "

    private val modelSerializer = SModelSerializer(repository)
    private val modelReferenceSerializer = SModelReferenceSerializer(repository)

    // [ModelWithModelReference: [see SModelSerializer.serialize(model)], modelReference: [see SModelReferenceSerializer.serialize(modelReference)]]
    override fun serialize(it: ModelWithModelReference): String {
        val serializedModel = modelSerializer.serialize(it.source)
        return serializeWithRhs(serializedModel, it, it.modelReference, modelReferenceSerializer)
    }

    override fun deserialize(from: String): ModelWithModelReference {
        val split = splitIntoLhsAndRhs(from)

        val model = modelSerializer.deserializeNotNull(split[0])
        val modelReference = modelReferenceSerializer.deserializeNotNull(split[1])

        return ModelWithModelReference(model, modelReference)
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal class ModelWithModuleReferenceSerializer(repository: SRepository? = null) :
    Serializer<ModelWithModuleReference>() {

    override val typeDelimiter = "ModelWithModuleReference"
    override val infixSeparator = ", moduleReference: "

    private val modelSerializer = SModelSerializer(repository)
    private val moduleReferenceSerializer = SModuleReferenceSerializer(repository)

    // [ModelWithModuleReference: [see SModelSerializer.serialize(model)], moduleReference: [see SModuleReferenceSerializer.serialize(moduleReference)]]
    override fun serialize(it: ModelWithModuleReference): String {
        val serializedModel = modelSerializer.serialize(it.source)
        return serializeWithRhs(serializedModel, it, it.moduleReference, moduleReferenceSerializer)
    }

    override fun deserialize(from: String): ModelWithModuleReference {
        val split = splitIntoLhsAndRhs(from)

        val model = modelSerializer.deserializeNotNull(split[0])
        val moduleReference = moduleReferenceSerializer.deserializeNotNull(split[1])

        return ModelWithModuleReference(model, moduleReference)
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal class ModuleWithModuleReferenceSerializer(repository: SRepository? = null) :
    Serializer<ModuleWithModuleReference>() {

    override val typeDelimiter = "ModuleWithModuleReference"
    override val infixSeparator = ", moduleReference: "

    private val moduleSerializer = SModuleSerializer(repository)
    private val moduleReferenceSerializer = SModuleReferenceSerializer(repository)

    // [ModuleWithModuleReference: [see SModuleReference.serialize(model)], moduleReference: [see SModuleReferenceSerializer.serialize(moduleReference)]]
    override fun serialize(it: ModuleWithModuleReference): String {
        val serializedModule = moduleSerializer.serialize(it.source)
        return serializeWithRhs(serializedModule, it, it.moduleReference, moduleReferenceSerializer)
    }

    override fun deserialize(from: String): ModuleWithModuleReference {
        val split = splitIntoLhsAndRhs(from)

        val module = moduleSerializer.deserializeNotNull(split[0])
        val moduleReference = moduleReferenceSerializer.deserializeNotNull(split[1])

        return ModuleWithModuleReference(module, moduleReference)
    }
}
