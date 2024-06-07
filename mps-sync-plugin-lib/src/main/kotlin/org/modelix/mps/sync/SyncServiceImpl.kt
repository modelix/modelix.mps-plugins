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
import org.modelix.model.lazy.CLVersion
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.EmptyBinding
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.bindings.ModuleBinding
import org.modelix.mps.sync.modelix.BranchRegistry
import org.modelix.mps.sync.modelix.ReplicatedModelInitContext
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.notifications.INotifier
import org.modelix.mps.sync.mps.notifications.InjectableNotifierWrapper
import org.modelix.mps.sync.mps.util.ModuleIdWithName
import org.modelix.mps.sync.mps.util.isDescriptorModel
import org.modelix.mps.sync.tasks.FuturesWaitQueue
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.modelixToMps.initial.ITreeToSTreeTransformer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModelSynchronizer
import org.modelix.mps.sync.transformation.mpsToModelix.initial.ModuleSynchronizer
import java.io.IOException
import java.net.URL

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
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
        BindingsRegistry.deactivateBindings(waitForCompletion = true)
        BranchRegistry.close()
        logger.info { "Bindings are deactivated and branch is disposed." }
    }

    override fun disconnectFromBranch(branch: IBranch, branchName: String) {
        logger.info { "Deactivating bindings and disposing cloned branch $branchName." }
        BindingsRegistry.deactivateBindings(waitForCompletion = true)
        BranchRegistry.unsetBranch(branch)
        logger.info { "Bindings are deactivated and branch ($branchName) is disposed." }
    }

    override fun getActiveBranch(): IBranch? = BranchRegistry.branch

    /**
     * WARNING: this is a long-running blocking call.
     */
    override fun connectToBranch(client: ModelClientV2, branchReference: BranchReference): IBranch =
        connectToBranch(client, branchReference, null)

    /**
     * WARNING: this is a long-running blocking call.
     */
    private fun connectToBranch(
        client: ModelClientV2,
        branchReference: BranchReference,
        initialVersion: CLVersion? = null,
    ): IBranch {
        logger.info { "Connecting to branch $branchReference with initial version $initialVersion (null = latest version)." }
        val targetProject = mpsProjectInjector.activeMpsProject!!
        val languageRepository = registerLanguages(targetProject)
        return runBlocking(dispatcher) {
            val branch = BranchRegistry.setBranch(
                client,
                branchReference,
                languageRepository,
                targetProject,
                ReplicatedModelInitContext(CoroutineScope(dispatcher), initialVersion),
            )
            logger.info { "Connected to branch $branchReference with initial version $initialVersion" }
            branch
        }
    }

    /**
     * WARNING:
     * 1. This is a long-running blocking call.
     * 2. Do not call this method from the main / EDT Thread, otherwise it will not be able to write to MPS!!!
     */
    override fun bindModuleFromServer(
        client: ModelClientV2,
        branchReference: BranchReference,
        module: ModuleIdWithName,
    ): Iterable<IBinding> {
        val moduleName = module.name
        logger.info { "Binding Module '$moduleName' from the server ($branchReference)." }

        val targetProject = mpsProjectInjector.activeMpsProject!!
        val languageRepository = registerLanguages(targetProject)

        // fetch replicated model and branch content
        val branch = connectToBranch(client, branchReference)

        // transform the modules and models
        val bindings = ITreeToSTreeTransformer(branch, languageRepository).transform(module.id)

        notifyUserAboutBindings(bindings, moduleName)

        return bindings
    }

    /**
     * WARNING:
     * 1. This is a long-running blocking call.
     * 2. From the Modelix-MPS synchronization point of view, we expect that the synchronization cache (MpsToModelixMap)
     * is already initialized with the mappings between the MPS elements and the Modelix Nodes. Otherwise, the change
     * listeners registered in this method will not work correctly.
     */
    override fun rebindModules(
        client: ModelClientV2,
        branchReference: BranchReference,
        initialVersion: CLVersion,
        modules: Iterable<AbstractModule>,
    ): Iterable<IBinding>? {
        if (!modules.iterator().hasNext()) {
            val message =
                "The list is restorable Modules is empty, therefore no Module- or Model Binding is restored for them."
            notifierInjector.notifyAndLogWarning(message, logger)
            return null
        }

        val branch = connectToBranch(client, branchReference, initialVersion)
        val bindings = mutableListOf<IBinding>()
        modules.forEach { module ->
            val moduleBinding = ModuleBinding(module, branch)
            BindingsRegistry.addModuleBinding(moduleBinding)

            module.models.forEach { model ->
                require(model is SModelBase) { "Model ($model) is not an SModelBase." }
                val binding = if (model.isDescriptorModel()) {
                    // We do not track changes in descriptor models. See ModelTransformer.isDescriptorModel()
                    EmptyBinding()
                } else {
                    val modelBinding = ModelBinding(model, branch)
                    BindingsRegistry.addModelBinding(modelBinding)
                    modelBinding
                }
                bindings.add(binding)
            }

            notifyUserAboutBindings(listOf(moduleBinding), module.moduleName)
            bindings.add(moduleBinding)
        }

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

        notifyUserAboutBindings(bindings, module.moduleName)

        return bindings
    }

    private fun notifyUserAboutBindings(bindings: Iterable<IBinding>, moduleName: String? = "null") {
        val hasAnyBinding = bindings.iterator().hasNext()
        if (hasAnyBinding) {
            val message = "Module- and Model Bindings for Module '$moduleName' are created."
            notifierInjector.notifyAndLogInfo(message, logger)
        } else {
            val message =
                "No Module- or Model Binding is created for Module '$moduleName'. This might be due to an error."
            notifierInjector.notifyAndLogWarning(message, logger)
        }
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
        logger.debug { "Closing SyncServiceImpl." }
        // dispose task and wait queues
        SyncQueue.close()
        FuturesWaitQueue.close()
        // dispose replicated model
        BranchRegistry.close()
        // dispose all bindings
        BindingsRegistry.deactivateBindings()
        logger.debug { "SyncServiceImpl is closed." }
    }

    private fun registerLanguages(project: MPSProject): MPSLanguageRepository {
        val repository = project.repository
        val mpsLanguageRepo = MPSLanguageRepository(repository)
        ILanguageRepository.register(mpsLanguageRepo)
        return mpsLanguageRepo
    }
}
