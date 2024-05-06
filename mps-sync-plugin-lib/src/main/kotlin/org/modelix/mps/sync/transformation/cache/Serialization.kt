package org.modelix.mps.sync.transformation.cache

import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SLanguage
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

abstract class Serializer<T> {

    protected abstract val typeDelimiter: String

    protected open val parentSeparator: String? = null

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

    protected fun <U> serializeWithParent(
        serializedChild: String,
        childToString: String,
        parent: U?,
        parentSerializer: Serializer<U>,
    ): String {
        requireNotNull(parent) { "$typeDelimiter ($childToString)'s parent is null." }
        val serializedParent = parentSerializer.serialize(parent)
        return "$serializedChild$parentSeparator$serializedParent"
    }

    // deserialization

    protected fun extractValue(from: String) = from.replace(serializationPrefix, "").replace(serializationSuffix, "")

    protected fun splitIntoParentAndChild(from: String, parentSeparator: String): List<String> {
        val serializedValue = extractValue(from)
        val split = serializedValue.split(parentSeparator)
        require(split.size == 2) { "Serialized value ($from) cannot be split into two parts via its parent separator ($parentSeparator)." }
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
    override val parentSeparator = ", parentModule: "

    private val parentSerializer = SModuleSerializer(repository)

    // [SModel: ID, parentModule: [see SModuleSerializer.serialize(module)]]
    override fun serialize(it: SModel) =
        serializeWithParent(it.getModelixId(), it.toString(), it.module, parentSerializer)

    override fun deserialize(from: String): SModel? {
        val split = splitIntoParentAndChild(from, parentSeparator)

        val parentId = split[1]
        val parent = parentSerializer.deserializeNotNull(parentId)

        val modelId = PersistenceFacade.getInstance().createModelId(split[0])
        return parent.getModel(modelId)
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal class SNodeSerializer(repository: SRepository? = null) : Serializer<SNode>() {

    override val typeDelimiter = "SNode"
    override val parentSeparator = ", parentModel: "

    private val parentSerializer = SModelSerializer(repository)

    // [SModel: ID, parentModel: [see SModelSerializer.serialize(model)]
    override fun serialize(it: SNode) =
        serializeWithParent(it.getModelixId(), it.toString(), it.model, parentSerializer)

    override fun deserialize(from: String): SNode? {
        val split = splitIntoParentAndChild(from, parentSeparator)

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
        return this.serialize(serializedModel)
    }

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
        return this.serialize(serializedModule)
    }

    override fun deserialize(from: String): SModuleReference? {
        val serializedModule = extractValue(from)
        val module = moduleSerializer.deserialize(serializedModule)
        return module?.moduleReference?.clone()
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
internal class LanguageSerializer(repository: SRepository? = null) : Serializer<SLanguage>() {

    override val typeDelimiter = "Language"

    private val moduleReferenceSerializer = SModuleReferenceSerializer(repository)

    // [SLanguage: [see SModuleReferenceSerializer.serialize(module)]
    override fun serialize(it: SLanguage): String {
        val moduleReference = it.sourceModuleReference
        val serializedModuleReference = moduleReferenceSerializer.serialize(moduleReference)
        return this.serialize(serializedModuleReference)
    }

    override fun deserialize(from: String): SLanguage {
        val serializedModuleReference = extractValue(from)
        val moduleReference = moduleReferenceSerializer.deserializeNotNull(serializedModuleReference)
        return MetaAdapterFactory.getLanguage(moduleReference)
    }
}
