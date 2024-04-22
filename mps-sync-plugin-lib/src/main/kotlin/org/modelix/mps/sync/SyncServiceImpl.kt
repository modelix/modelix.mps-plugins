package org.modelix.mps.sync

import com.intellij.openapi.project.Project
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
import java.io.IOException
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SyncServiceImpl : SyncService {

    private val logger = KotlinLogging.logger {}
    private val mpsProjectInjector = ActiveMpsProjectInjector

    private val dispatcher = Dispatchers.Default

    init {
        logger.info { "============================================ Registering builtin languages" }
        // just a dummy call, the initializer of ILanguageRegistry takes care of the rest...
        ILanguageRepository.default.javaClass
    }

    @Throws(IOException::class)
    override fun connectModelServer(
        serverURL: URL,
        jwt: String?,
        callback: (() -> Unit)?,
    ): ModelClientV2 {
        logger.info { "Connecting to $serverURL" }
        val modelClientV2 = ModelClientV2.builder().url(serverURL.toString()).authToken { jwt }.build()
        runBlocking(dispatcher) {
            modelClientV2.init()
        }
        logger.info { "Connection to $serverURL successful" }

        callback?.invoke()

        return modelClientV2
    }

    override fun connectToBranch(client: ModelClientV2, branchReference: BranchReference): IBranch {
        val targetProject = mpsProjectInjector.activeMpsProject!!
        val languageRepository = registerLanguages(targetProject)
        return runBlocking(dispatcher) {
            BranchRegistry.setBranch(client, branchReference, languageRepository, targetProject)
        }
    }

    override fun disconnectModelServer(
        client: ModelClientV2,
        callback: (() -> Unit)?,
    ) {
        // TODO what shall happen with the bindings if we disconnect from model server?
        client.close()
        callback?.invoke()
    }

    override fun bindModuleFromServer(
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

    override fun bindModuleFromMps(module: AbstractModule, branch: IBranch): Iterable<IBinding> {
        logger.info { "Binding Module ${module.moduleName} to the server" }

        // warning: blocking call
        @Suppress("UNCHECKED_CAST")
        val bindings = ModuleSynchronizer(branch).addModule(module, true).getResult().get() as Iterable<IBinding>
        logger.info { "Module and ModelBindings for Module ${module.moduleName} are created" }

        return bindings
    }

    override fun bindModelFromMps(model: SModelBase, branch: IBranch): IBinding {
        logger.info { "Binding Model ${model.name} to the server" }

        val synchronizer = ModelSynchronizer(branch)
        // synchronize model. Warning: blocking call
        val binding = synchronizer.addModel(model).getResult().get() as IBinding
        // wait until the model imports are synced. Warning: blocking call
        synchronizer.resolveModelImportsInTask().getResult().get()

        logger.info { "ModelBinding for ${model.name} is created" }
        return binding
    }

    override fun setActiveProject(project: Project) {
        mpsProjectInjector.setActiveProject(project)
    }

    override fun dispose() {
        // dispose task and wait queues
        SyncQueue.close()
        FuturesWaitQueue.close()
        // dispose replicated model
        BranchRegistry.dispose()
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
