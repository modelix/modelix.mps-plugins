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

/**
 * The synchronization coordinator class, that can connect to the model server and bind models and modules in both
 * directions, starting from the model server or from MPS as well.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class SyncServiceImpl : ISyncService, InjectableService {

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    /**
     * A coroutine dispatcher for rather IO-intensive tasks.
     */
    private val networkDispatcher = Dispatchers.IO

    /**
     * A coroutine dispatcher for rather CPU-intensive tasks.
     */
    private val cpuDispatcher = Dispatchers.Default

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

    /**
     * Disconnects the [client] from the model server. After the [client] is closed, we deactivate all bindings in the
     * [bindingsRegistry] and dispose the [branchRegistry] too. So that we will have a completely clean state with no
     * active binding or connected branch.
     *
     * ⚠️ WARNING ⚠️: this is a long-running blocking call.
     *
     * @param client the client to disconnect from the model server.
     *
     * @see [ModelClientV2.close].
     * @see [BindingsRegistry.deactivateBindings].
     * @see [BranchRegistry.dispose].
     * @see [ISyncService.disconnectModelServer].
     */
    override fun disconnectModelServer(client: ModelClientV2) {
        logger.info { "Disconnecting from ${client.baseUrl}" }
        client.close()
        logger.info { "Disconnected from ${client.baseUrl}" }

        logger.info { "Deactivating bindings and disposing cloned branch." }
        bindingsRegistry.deactivateBindings(waitForCompletion = true)
        branchRegistry.dispose()
        logger.info { "Bindings are deactivated and branch is disposed." }
    }

    /**
     * Disconnects the [branch] that is called [branchName] from the model server. We deactivate all bindings in the
     * [bindingsRegistry] and dispose the [branchRegistry] too. So that we will have a completely clean state with no
     * active binding or connected branch.
     *
     * ⚠️ WARNING ⚠️: this is a long-running blocking call.
     *
     * @param branch the branch to disconnect from the model server.
     * @param branchName the name of the branch.
     *
     * @see [ISyncService.disconnectFromBranch].
     */
    override fun disconnectFromBranch(branch: IBranch, branchName: String) {
        logger.info { "Deactivating bindings and disposing cloned branch $branchName." }
        bindingsRegistry.deactivateBindings(waitForCompletion = true)
        branchRegistry.unsetBranch(branch)
        logger.info { "Bindings are deactivated and branch ($branchName) is disposed." }
    }

    override fun getActiveBranch(): IBranch? = branchRegistry.getBranch()

    /**
     * Connects the branch identified by its [branchReference], using the [client] model client.
     *
     * ⚠️ WARNING ⚠️: this is a long-running blocking call.
     *
     * @param client the model client to use for the connection.
     * @param branchReference the identifier of the branch to connect to.
     *
     * @return a reference for the connected branch.
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

    /**
     * Calls [BranchRegistry.setReplicatedModel] to fetch the branch identified by [branchReference] with the version
     * denoted by [initialVersion].
     *
     * @param client the model client to use for the connection.
     * @param branchReference the identifier of the branch.
     * @param initialVersion the version of the branch that we want to use.
     *
     * @return the [ReplicatedModel] that is a live connection to the data on the branch.
     */
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
     * Binds a Module and the transitively reachable Modules, Models and Nodes to MPS. I.e. it downloads and transforms
     * the modelix nodes to the corresponding MPS elements and finally establishes [IBinding]s between the model server
     * and MPS for the synchronized Modules and Models.
     *
     * ⚠️ WARNING ⚠️: this is a long-running blocking call.
     *
     * @param client the model client to be used for the connection.
     * @param branchReference the identifier of the branch from which we download the Modules, Models and Nodes.
     * @param module the ID and the name of the Module that is the starting point of the transformation.
     *
     * @return an [Iterable] of the [IBinding]s that were created for the synchronized Modules and Models.
     *
     * @see [ISyncService.bindModuleFromServer].
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
     * Connects to the model server's specific branch's specific version, and creates [IBinding]s for the modules and
     * their models. So that changes in the respective modules and models will be reflected on the model server.
     *
     * ⚠️ WARNING ⚠️: this is a long-running blocking call.
     *
     * @param client the model server connection.
     * @param branchReference to which branch we want to connect.
     * @param initialVersion which version in the branch history we want to use.
     * @param modules for which MPS modules and their models we want to create [IBinding]s.
     *
     * @return the [IBinding]s that are created for the modules and their models.
     *
     * @see [IRebindModulesSyncService.rebindModules].
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
     * Synchronizes the local MPS [module] to the modelix [branch].
     *
     * ⚠️ WARNING ⚠️: this is a long-running blocking call.
     *
     * @param module the MPS Module to synchronize to the model server.
     * @param branch the modelix branch to which we want to synchronize the [module].
     *
     * @return the [IBinding]s that were created for the synchronized Modules and Models.
     *
     * @see [ISyncService.bindModuleFromMps].
     */
    override fun bindModuleFromMps(module: AbstractModule, branch: IBranch): Iterable<IBinding> {
        logger.info { "Binding Module '${module.moduleName}' to the server." }

        @Suppress("UNCHECKED_CAST")
        val bindings = ModuleSynchronizer(branch, serviceLocator)
            .addModule(module, true)
            .getResult().get() as Iterable<IBinding>

        notifyUserAboutBindings(bindings, module.moduleName)

        return bindings
    }

    /**
     * Notifies the user about the fact that several or none bindings, depending on the size of [bindings], are created
     * for the module called [moduleName]. The user notification is done by the [notifier]. The message is logged by the
     * [logger].
     *
     * @param bindings the [IBinding]s that were created, or an empty [Iterable] if no binding was created.
     * @param moduleName the name of the MPS Module that was the entry point of the synchronization.
     */
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
     * Synchronizes the local MPS [model] to the modelix [branch].
     *
     * ⚠️ WARNING ⚠️: this is a long-running blocking call.
     *
     * @param model the MPS Model to synchronize to the model server.
     * @param branch the modelix branch to which we want to synchronize the [model].
     *
     * @return the [ModelBinding] that was created for the synchronized Model.
     *
     * @see [ISyncService.bindModelFromMps].
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
