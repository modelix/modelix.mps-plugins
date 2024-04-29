package org.modelix.mps.sync

import com.intellij.openapi.project.Project
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
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
import org.modelix.mps.sync.bindings.EmptyBinding
import org.modelix.mps.sync.modelix.BranchRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.notifications.INotifier
import org.modelix.mps.sync.mps.notifications.InjectableNotifierWrapper
import org.modelix.mps.sync.tasks.FuturesWaitQueue
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.modelixToMps.initial.ITreeToSTreeTransformer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModelSynchronizer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModuleSynchronizer
import java.io.IOException
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SyncServiceImpl(userNotifier: INotifier) : ISyncService {

    private val logger = KotlinLogging.logger {}
    private val mpsProjectInjector = ActiveMpsProjectInjector
    private val notifierInjector = InjectableNotifierWrapper

    private val dispatcher = Dispatchers.IO // rather IO-intensive tasks

    init {
        notifierInjector.notifier = userNotifier

        logger.debug { "ModelixSyncPlugin: Registering built-in languages" }
        // just a dummy call, the initializer of ILanguageRegistry takes care of the rest...
        ILanguageRepository.default.javaClass
    }

    @Throws(IOException::class)
    override fun connectModelServer(serverURL: URL, jwt: String?): ModelClientV2 {
        logger.info { "Connecting to $serverURL" }
        val modelClientV2 = ModelClientV2.builder().url(serverURL.toString()).authToken { jwt }.build()
        runBlocking(dispatcher) {
            modelClientV2.init()
        }
        logger.info { "Connection to $serverURL is successful." }

        return modelClientV2
    }

    override fun disconnectModelServer(client: ModelClientV2) {
        logger.info { "Disconnecting from ${client.baseUrl}" }
        client.close()
        logger.info { "Disconnected from ${client.baseUrl}" }

        logger.info { "Deactivating bindings and disposing cloned branch." }
        BindingsRegistry.deactivateBindings()
        BranchRegistry.close()
        logger.info { "Bindings are deactivated and branch is disposed." }
    }

    override fun disconnectFromBranch(branch: IBranch) {
        val branchId = branch.getId()
        logger.info { "Deactivating bindings and disposing cloned branch $branchId." }
        BindingsRegistry.deactivateBindings()
        BranchRegistry.unsetBranch(branch)
        logger.info { "Bindings are deactivated and branch ($branchId) is disposed." }
    }

    override fun getActiveBranch(): IBranch? = BranchRegistry.branch

    /**
     * WARNING: this is a long-running blocking call.
     */
    override fun connectToBranch(client: ModelClientV2, branchReference: BranchReference): IBranch {
        val targetProject = mpsProjectInjector.activeMpsProject!!
        val languageRepository = registerLanguages(targetProject)
        return runBlocking(dispatcher) {
            BranchRegistry.setBranch(
                client,
                branchReference,
                languageRepository,
                targetProject,
                CoroutineScope(dispatcher),
            )
        }
    }

    /**
     * WARNING: this is a long-running blocking call.
     * Do not call this method from the main / EDT Thread, otherwise it will not be able to write to MPS!!!
     */
    override fun bindModuleFromServer(
        client: ModelClientV2,
        branchReference: BranchReference,
        moduleId: String,
    ): Iterable<IBinding> {
        val targetProject = mpsProjectInjector.activeMpsProject!!
        val languageRepository = registerLanguages(targetProject)

        // fetch replicated model and branch content
        val branch = connectToBranch(client, branchReference)

        // transform the modules and models
        val bindings = ITreeToSTreeTransformer(branch, languageRepository).transform(moduleId)

        return bindings
    }

    /**
     * WARNING: this is a long-running blocking call.
     */
    override fun bindModuleFromMps(module: AbstractModule, branch: IBranch): Iterable<IBinding> {
        logger.info { "Binding Module '${module.moduleName}' to the server." }

        // warning: blocking call
        @Suppress("UNCHECKED_CAST")
        val bindings = ModuleSynchronizer(branch).addModule(module, true).getResult().get() as Iterable<IBinding>
        val hasAnyBinding = bindings.iterator().hasNext()

        if (hasAnyBinding) {
            val message = "Module- and Model Bindings for Module '${module.moduleName}' are created."
            notifierInjector.notifyAndLogInfo(message, logger)
        } else {
            val message =
                "No Module- or Model Binding is created for Module '${module.moduleName}'. This might be due to an error."
            notifierInjector.notifyAndLogWarning(message, logger)
        }

        return bindings
    }

    /**
     * WARNING: this is a long-running blocking call.
     */
    override fun bindModelFromMps(model: SModelBase, branch: IBranch): IBinding {
        logger.info { "Binding Model '${model.name}' to the server." }

        val synchronizer = ModelSynchronizer(branch)
        // synchronize model. Warning: blocking call
        val binding = synchronizer.addModel(model).getResult().get() as IBinding
        // wait until the model imports are synced. Warning: blocking call
        synchronizer.resolveModelImportsInTask().getResult().get()

        if (binding !is EmptyBinding) {
            val message = "Model Binding for '${model.name}' is created."
            notifierInjector.notifyAndLogInfo(message, logger)
        } else {
            val message = "No Model Binding is created for '${model.name}'. This might be due to an error."
            notifierInjector.notifyAndLogWarning(message, logger)
        }

        return binding
    }

    override fun setActiveProject(project: Project) {
        mpsProjectInjector.setActiveProject(project)
    }

    override fun close() {
        // dispose task and wait queues
        SyncQueue.close()
        FuturesWaitQueue.close()
        // dispose replicated model
        BranchRegistry.close()
        // dispose all bindings
        BindingsRegistry.deactivateBindings()
    }

    private fun registerLanguages(project: MPSProject): MPSLanguageRepository {
        val repository = project.repository
        val mpsLanguageRepo = MPSLanguageRepository(repository)
        ILanguageRepository.register(mpsLanguageRepo)
        return mpsLanguageRepo
    }
}
