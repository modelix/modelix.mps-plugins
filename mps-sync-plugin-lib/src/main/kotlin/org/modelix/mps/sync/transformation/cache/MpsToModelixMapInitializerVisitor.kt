package org.modelix.mps.sync.transformation.cache

import jetbrains.mps.extapi.model.SModelBase
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeReference
import org.modelix.model.mpsadapters.MPSArea
import org.modelix.model.mpsadapters.MPSModelImportAsNode
import org.modelix.mps.sync.modelix.tree.IBranchVisitor
import org.modelix.mps.sync.modelix.util.getModel
import org.modelix.mps.sync.modelix.util.getMpsNodeId
import org.modelix.mps.sync.modelix.util.nodeIdAsLong
import org.modelix.mps.sync.mps.util.runReadAction

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class MpsToModelixMapInitializerVisitor(
    private val cache: MpsToModelixMap,
    private val repository: SRepository,
    private val branch: IBranch,
) : IBranchVisitor {

    private val logger = KotlinLogging.logger {}

    override suspend fun visitModule(node: INode) = runWithReadLocks {
        val module = getMpsModule(node)
        val nodeId = node.nodeIdAsLong()
        cache.put(module, nodeId)
    }

    override suspend fun visitModel(node: INode) = runWithReadLocks {
        val model = getMpsModel(node)
        val nodeId = node.nodeIdAsLong()
        cache.put(model, nodeId)
    }

    override suspend fun visitNode(node: INode) = runWithReadLocks {
        val nodeId = node.nodeIdAsLong()
        val modelNode = node.getModel()
        requireNotNull(modelNode) { "Model parent of Modelix Node '$nodeId' is not found." }
        val mpsModel = getMpsModel(modelNode)

        val mpsNodeId = node.getMpsNodeId()
        val mpsNode = mpsModel.getNode(mpsNodeId)
        requireNotNull(mpsNode) { "Node with ID '$mpsNodeId' is not found." }

        cache.put(mpsNode, nodeId)
    }

    override suspend fun visitModuleDependency(sourceModule: INode, moduleDependency: INode) = runWithReadLocks {
        val module = getMpsModule(sourceModule)
        val nodeId = moduleDependency.nodeIdAsLong()

        val targetModuleId =
            moduleDependency.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.uuid)
        requireNotNull(targetModuleId) { "UUID of Modelix Module Dependency node '$nodeId' is null." }
        val moduleId = PersistenceFacade.getInstance().createModuleId(targetModuleId)

        if (module.moduleId == moduleId) {
            logger.warn { "Self-dependency of Module ($module) is ignored." }
            return@runWithReadLocks
        }

        val targetModuleDependency =
            module.declaredDependencies.firstOrNull { it.targetModule.moduleId == moduleId }
        requireNotNull(targetModuleDependency) {
            val targetModuleName =
                moduleDependency.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency.name)
            "Module '${module.moduleName}' has no Module Dependency with ID '$targetModuleId' and name '$targetModuleName'."
        }

        cache.put(module, targetModuleDependency.targetModule, nodeId)
    }

    override suspend fun visitDevKitDependency(sourceModel: INode, devKitDependency: INode) = runWithReadLocks {
        val model = getMpsModel(sourceModel)
        require(model is SModelBase) { "Model '${model.name}' (parent Module: ${model.module?.moduleName}) is not an SModelBase." }

        val nodeId = devKitDependency.nodeIdAsLong()
        val targetModuleId =
            devKitDependency.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)
        requireNotNull(targetModuleId) { "UUID of Modelix DevKit Dependency node '$nodeId' is null." }
        val moduleId = PersistenceFacade.getInstance().createModuleId(targetModuleId)

        val targetDevKitDependency = model.importedDevkits().firstOrNull { it.moduleId == moduleId }
        requireNotNull(targetDevKitDependency) {
            val targetDevKitName =
                devKitDependency.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name)
            "Model '${model.name}' (parent Module: ${model.module?.moduleName}) has no DevKit Dependency with ID '$targetModuleId' and name '$targetDevKitName'."
        }

        cache.put(model, targetDevKitDependency, nodeId)
    }

    override suspend fun visitLanguageDependency(sourceModel: INode, languageDependency: INode) = runWithReadLocks {
        val model = getMpsModel(sourceModel)
        require(model is SModelBase) { "Model '${model.name}' (parent Module: ${model.module?.moduleName}) is not an SModelBase." }

        val nodeId = languageDependency.nodeIdAsLong()
        val targetModuleId =
            languageDependency.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.uuid)
        requireNotNull(targetModuleId) { "UUID of Modelix DevKit Dependency node '$nodeId' is null." }
        val moduleId = PersistenceFacade.getInstance().createModuleId(targetModuleId)

        val targetLanguageDependency =
            model.importedLanguageIds()
                .firstOrNull { it.sourceModuleReference.moduleId == moduleId }?.sourceModuleReference
        requireNotNull(targetLanguageDependency) {
            val targetDevKitName =
                languageDependency.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.LanguageDependency.name)
            "Model '${model.name}' (parent Module: ${model.module?.moduleName}) has no Language Dependency with ID '$targetModuleId' and name '$targetDevKitName'."
        }

        cache.put(model, targetLanguageDependency, nodeId)
    }

    override suspend fun visitModelImport(sourceModel: INode, modelImport: INode) = runWithReadLocks {
        val nodeId = modelImport.nodeIdAsLong()
        val model = getMpsModel(sourceModel)
        require(model is SModelBase) { "Model '${model.name}' (parent Module: ${model.module?.moduleName}) is not an SModelBase." }

        val targetModelRef =
            modelImport.getReferenceTargetRef(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)!!
        val serializedModelRef = targetModelRef.serialize()

        val targetIsAnINode = PNodeReference.tryDeserialize(serializedModelRef) != null
        val (targetModelId, targetModelName) = if (targetIsAnINode) {
            val targetModel =
                modelImport.getReferenceTarget(BuiltinLanguages.MPSRepositoryConcepts.ModelReference.model)
            val targetModelId = targetModel?.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id)
            requireNotNull(targetModelId) { "ID of Modelix Model referred by Modelix Model Import Node '$nodeId' is null." }
            val modelId = PersistenceFacade.getInstance().createModelId(targetModelId)
            val modelName = targetModel.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
            Pair(modelId, modelName)
        } else {
            // target is an SModel in MPS
            val modelixModelImport = MPSArea(repository).resolveNode(targetModelRef) as MPSModelImportAsNode
            val targetModel = modelixModelImport.importedModel
            val modelId = targetModel.modelId
            val modelName = targetModel.name
            Pair(modelId, modelName)
        }

        if (model.modelId == targetModelId) {
            logger.warn { "Ignoring Model Import from Model ${model.name} (parent Module: ${model.module?.moduleName}) to itself." }
            return@runWithReadLocks
        }

        val targetModelImport = model.modelImports.firstOrNull { it.modelId == targetModelId }
        requireNotNull(targetModelImport) {
            "Model '${model.name}' (parent Module: ${model.module?.moduleName}) has no Model Import with ID '$targetModelId' and name '$targetModelName'."
        }

        cache.put(model, targetModelImport, nodeId)
    }

    private fun getMpsModel(modelNode: INode): SModel {
        val modelixModelId = modelNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Model.id)
        requireNotNull(modelixModelId) {
            val nodeId = modelNode.nodeIdAsLong()
            "Model ID is null in Modelix Node '$nodeId'."
        }
        val modelId = PersistenceFacade.getInstance().createModelId(modelixModelId)
        val model = repository.getModel(modelId)
        requireNotNull(model) { "Model with ID '$modelId' is not found." }
        return model
    }

    private fun getMpsModule(moduleNode: INode): SModule {
        val modelixModuleId = moduleNode.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id)
        requireNotNull(modelixModuleId) {
            val nodeId = moduleNode.nodeIdAsLong()
            "Module ID is null in Modelix Node '$nodeId'."
        }
        val moduleId = PersistenceFacade.getInstance().createModuleId(modelixModuleId)
        val module = repository.getModule(moduleId)
        requireNotNull(module) { "Module with ID '$moduleId' is not found." }
        return module
    }

    private fun runWithReadLocks(action: () -> Unit) = repository.runReadAction { branch.runRead { action() } }
}
