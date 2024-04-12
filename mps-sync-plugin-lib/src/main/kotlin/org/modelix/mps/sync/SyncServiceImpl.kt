package org.modelix.mps.sync

import com.intellij.openapi.project.Project
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.modelix.BranchRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.tasks.FuturesWaitQueue
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.modelixToMps.initial.ITreeToSTreeTransformer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModelSynchronizer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModuleSynchronizer
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SyncServiceImpl : SyncService {

    private val logger = KotlinLogging.logger {}
    private val mpsProjectInjector = ActiveMpsProjectInjector

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val activeClients = mutableSetOf<ModelClientV2>()

    init {
        logger.info { "============================================ Registering builtin languages" }
        // just a dummy call, the initializer of ILanguageRegistry takes care of the rest...
        ILanguageRepository.default.javaClass
    }

    override suspend fun connectModelServer(
        serverURL: URL,
        jwt: String,
        callback: (() -> Unit)?,
    ): ModelClientV2 {
        // avoid reconnect to existing server
        val client = activeClients.find { it.baseUrl == serverURL.toString() }
        client?.let {
            logger.info { "Using already existing connection to $serverURL" }
            return it
        }

        logger.info { "Connecting to $serverURL" }
        // TODO: use JWT here
        val modelClientV2 = ModelClientV2.builder().url(serverURL.toString()).build()
        modelClientV2.init()

        logger.info { "Connection to $serverURL successful" }
        activeClients.add(modelClientV2)

        callback?.invoke()

        return modelClientV2
    }

    override suspend fun connectToBranch(client: ModelClientV2, branchReference: BranchReference): IBranch {
        val targetProject = mpsProjectInjector.activeMpsProject!!
        val languageRepository = registerLanguages(targetProject)

        return BranchRegistry.setBranch(client, branchReference, languageRepository, targetProject)
    }

    override suspend fun bindModuleFromServer(
        client: ModelClientV2,
        branchReference: BranchReference,
        moduleId: String,
        callback: (() -> Unit)?,
    ): Iterable<IBinding> {
        val targetProject = mpsProjectInjector.activeMpsProject!!
        val languageRepository = registerLanguages(targetProject)

        // fetch replicated model and branch content
        val branch = connectToBranch(client, branchReference)

        // transform the model
        val bindings = ITreeToSTreeTransformer(branch, languageRepository).transform(moduleId)

        // trigger callback after activation
        callback?.invoke()

        return bindings
    }

    override fun bindModuleFromMps(module: AbstractModule): Iterable<IBinding> {
        logger.info { "Binding Module ${module.moduleName} to the server" }

        val branch = BranchRegistry.branch
        require(branch != null) { "Connect to a server and branch before synchronizing a module" }

        // warning: blocking call
        @Suppress("UNCHECKED_CAST")
        val bindings = ModuleSynchronizer(branch).addModule(module).getResult().get() as Iterable<IBinding>
        logger.info { "Module and ModelBindings for Module ${module.moduleName} are created" }

        return bindings
    }

    override fun bindModelFromMps(model: SModelBase): IBinding {
        logger.info { "Binding Model ${model.name} to the server" }

        val branch = BranchRegistry.branch
        require(branch != null) { "Connect to a server and branch before synchronizing a model" }

        // warning: blocking call
        val binding = ModelSynchronizer(branch).addModel(model).getResult().get() as IBinding
        logger.info { "ModelBinding for ${model.name} is created" }
        return binding
    }

    override fun disconnectModelServer(
        client: ModelClientV2,
        callback: (() -> Unit)?,
    ) {
        // TODO what shall happen with the bindings and the branch if we disconnect from model server?
        activeClients.remove(client)
        client.close()
        callback?.invoke()
    }

    override fun setActiveProject(project: Project) {
        mpsProjectInjector.setActiveProject(project)
    }

    override fun dispose() {
        // cancel all running coroutines
        coroutineScope.cancel()
        SyncQueue.close()
        FuturesWaitQueue.close()
        // dispose replicated model
        BranchRegistry.dispose()
        // dispose clients
        activeClients.forEach { it.close() }
        // dispose all bindings
        BindingsRegistry.getModuleBindings().forEach { it.deactivate(removeFromServer = false) }
        BindingsRegistry.getModelBindings().forEach { it.deactivate(removeFromServer = false) }
    }

    private fun registerLanguages(project: MPSProject): MPSLanguageRepository {
        val repository = project.repository
        val mpsLanguageRepo = MPSLanguageRepository(repository)
        ILanguageRepository.register(mpsLanguageRepo)
        return mpsLanguageRepo
    }
}
