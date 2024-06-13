package org.modelix.mps.sync

import com.intellij.openapi.project.Project
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.bindings.EmptyBinding
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.bindings.ModuleBinding
import org.modelix.mps.sync.modelix.BranchRegistry
import org.modelix.mps.sync.modelix.ITreeTraversal
import org.modelix.mps.sync.modelix.ReplicatedModelInitContext
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.notifications.INotifier
import org.modelix.mps.sync.mps.notifications.InjectableNotifierWrapper
import org.modelix.mps.sync.mps.util.ModuleIdWithName
import org.modelix.mps.sync.mps.util.isDescriptorModel
import org.modelix.mps.sync.tasks.FuturesWaitQueue
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap
import org.modelix.mps.sync.transformation.cache.MpsToModelixMapInitializerVisitor
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

    private val networkDispatcher = Dispatchers.IO // rather IO-intensive tasks
    private val cpuDispatcher = Dispatchers.Default // rather CPU-intensive tasks

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
        runBlocking(networkDispatcher) {
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
        runBlocking(networkDispatcher) {
            val model = setReplicatedModel(client, branchReference)
            try {
                model.start()
            } catch (ignored: IllegalStateException) {
                // Start may throw exception if ReplicatedModel is already started. But it should not disturb us.
            }
            model.getBranch()
        }

    private fun setReplicatedModel(
        client: ModelClientV2,
        branchReference: BranchReference,
        initialVersion: CLVersion? = null,
    ): ReplicatedModel {
        logger.info { "Connecting to branch $branchReference with initial version $initialVersion (null = latest version)." }
        val targetProject = mpsProjectInjector.activeMpsProject!!
        val languageRepository = registerLanguages(targetProject)
        val model = BranchRegistry.setReplicatedModel(
            client,
            branchReference,
            languageRepository,
            targetProject,
            ReplicatedModelInitContext(CoroutineScope(networkDispatcher), initialVersion),
        )
        logger.info { "Connected to branch $branchReference with initial version $initialVersion" }
        return model
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
     * WARNING: this is a long-running blocking call.
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

        // connect to modelix and fetch the initial version
        val replicatedModel = setReplicatedModel(client, branchReference, initialVersion)

        // recreate the mapping between the local MPS elements and the modelix Nodes
        val branch = replicatedModel.getBranch()
        runBlocking(cpuDispatcher) {
            val repository = ActiveMpsProjectInjector.activeMpsProject?.repository
            requireNotNull(repository) { "SRepository must exist, otherwise we cannot restore the Modules." }
            val mappingRecreator = MpsToModelixMapInitializerVisitor(MpsToModelixMap, repository, branch)
            val treeTraversal = ITreeTraversal(branch)
            treeTraversal.visit(mappingRecreator)
        }

        // register the bindings
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

        // let modelix get the changes from model server
        CoroutineScope(networkDispatcher).launch { replicatedModel.start() }

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
