package org.modelix.mps.sync

import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
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
import org.modelix.mps.sync.modelix.branch.BranchRegistry
import org.modelix.mps.sync.modelix.branch.ReplicatedModelInitContext
import org.modelix.mps.sync.modelix.tree.ITreeTraversal
import org.modelix.mps.sync.mps.notifications.WrappedNotifier
import org.modelix.mps.sync.mps.services.InjectableService
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.mps.util.ModuleIdWithName
import org.modelix.mps.sync.mps.util.isDescriptorModel
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
class SyncServiceImpl : ISyncService, InjectableService {

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    private val networkDispatcher = Dispatchers.IO // rather IO-intensive tasks
    private val cpuDispatcher = Dispatchers.Default // rather CPU-intensive tasks

    /**
     * A notifier that can notify the user about certain messages in a nicer way than just simply logging the message.
     */
    private val notifier: WrappedNotifier
        get() = serviceLocator.wrappedNotifier

    /**
     * The registry to store the [IBinding]s.
     */
    private val bindingsRegistry: BindingsRegistry
        get() = serviceLocator.bindingsRegistry

    /**
     * A registry to store the modelix [IBranch] we are connected to.
     */
    private val branchRegistry: BranchRegistry
        get() = serviceLocator.branchRegistry

    /**
     * The active [SRepository] to access the [SModel]s and [SModule]s in MPS.
     */
    private val mpsRepository: SRepository
        get() = serviceLocator.mpsRepository

    /**
     * The [ILanguageRepository] that can resolve Concept UIDs of modelix nodes to Concepts in MPS.
     */
    private val languageRepository: MPSLanguageRepository
        get() = serviceLocator.languageRepository

    /**
     * A collector class to simplify injecting the commonly used services in the sync plugin.
     */
    private lateinit var serviceLocator: ServiceLocator

    override fun initService(serviceLocator: ServiceLocator) {
        this.serviceLocator = serviceLocator
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
        bindingsRegistry.deactivateBindings(waitForCompletion = true)
        branchRegistry.dispose()
        logger.info { "Bindings are deactivated and branch is disposed." }
    }

    override fun disconnectFromBranch(branch: IBranch, branchName: String) {
        logger.info { "Deactivating bindings and disposing cloned branch $branchName." }
        bindingsRegistry.deactivateBindings(waitForCompletion = true)
        branchRegistry.unsetBranch(branch)
        logger.info { "Bindings are deactivated and branch ($branchName) is disposed." }
    }

    override fun getActiveBranch(): IBranch? = branchRegistry.getBranch()

    /**
     * ⚠️ WARNING ⚠️: this is a long-running blocking call.
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
        val model = branchRegistry.setReplicatedModel(
            client,
            branchReference,
            languageRepository,
            ReplicatedModelInitContext(CoroutineScope(networkDispatcher), initialVersion),
        )
        logger.info { "Connected to branch $branchReference with initial version $initialVersion" }
        return model
    }

    /**
     * ⚠️ WARNING ⚠️:
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
        // fetch replicated model and branch content
        val branch = connectToBranch(client, branchReference)

        // transform the modules and models
        val bindings = ITreeToSTreeTransformer(branch, languageRepository, serviceLocator).transform(module.id)

        notifyUserAboutBindings(bindings, moduleName)

        return bindings
    }

    /**
     * ⚠️ WARNING ⚠️: this is a long-running blocking call.
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
            notifier.notifyAndLogWarning(message, logger)
            return null
        }

        // connect to modelix and fetch the initial version
        val replicatedModel = setReplicatedModel(client, branchReference, initialVersion)

        // recreate the mapping between the local MPS elements and the modelix Nodes
        val branch = replicatedModel.getBranch()
        runBlocking(cpuDispatcher) {
            val mappingRecreator = MpsToModelixMapInitializerVisitor(serviceLocator.nodeMap, mpsRepository, branch)
            val treeTraversal = ITreeTraversal(branch)
            treeTraversal.visit(mappingRecreator)
        }

        // register the bindings
        val bindings = mutableListOf<IBinding>()
        modules.forEach { module ->
            val moduleBinding = ModuleBinding(module, branch, serviceLocator)
            bindingsRegistry.addModuleBinding(moduleBinding)

            module.models.forEach { model ->
                require(model is SModelBase) { "Model ($model) is not an SModelBase." }
                val binding = if (model.isDescriptorModel()) {
                    // We do not track changes in descriptor models. See ModelTransformer.isDescriptorModel()
                    EmptyBinding()
                } else {
                    val modelBinding = ModelBinding(model, branch, serviceLocator)
                    bindingsRegistry.addModelBinding(modelBinding)
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
     * ⚠️ WARNING ⚠️: this is a long-running blocking call.
     */
    override fun bindModuleFromMps(module: AbstractModule, branch: IBranch): Iterable<IBinding> {
        logger.info { "Binding Module '${module.moduleName}' to the server." }

        // ⚠️ WARNING ⚠️: blocking call
        @Suppress("UNCHECKED_CAST")
        val bindings = ModuleSynchronizer(branch, serviceLocator)
            .addModule(module, true)
            .getResult().get() as Iterable<IBinding>

        notifyUserAboutBindings(bindings, module.moduleName)

        return bindings
    }

    private fun notifyUserAboutBindings(bindings: Iterable<IBinding>, moduleName: String? = "null") {
        val hasAnyBinding = bindings.iterator().hasNext()
        if (hasAnyBinding) {
            val message = "Module- and Model Bindings for Module '$moduleName' are created."
            notifier.notifyAndLogInfo(message, logger)
        } else {
            val message =
                "No Module- or Model Binding is created for Module '$moduleName'. This might be due to an error."
            notifier.notifyAndLogWarning(message, logger)
        }
    }

    /**
     * ⚠️ WARNING ⚠️: this is a long-running blocking call.
     */
    override fun bindModelFromMps(model: SModelBase, branch: IBranch): IBinding {
        logger.info { "Binding Model '${model.name}' to the server." }

        val synchronizer = ModelSynchronizer(branch, serviceLocator = serviceLocator)
        // synchronize model. ⚠️ WARNING ⚠️: blocking call
        val binding = synchronizer.addModel(model).getResult().get() as IBinding
        // wait until the model imports are synced. ⚠️ WARNING ⚠️: blocking call
        synchronizer.resolveModelImportsInTask().getResult().get()

        if (binding !is EmptyBinding) {
            val message = "Model Binding for '${model.name}' is created."
            notifier.notifyAndLogInfo(message, logger)
        } else {
            val message = "No Model Binding is created for '${model.name}'. This might be due to an error."
            notifier.notifyAndLogWarning(message, logger)
        }

        return binding
    }
}
