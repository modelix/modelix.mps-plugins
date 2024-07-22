@file:OptIn(UnstableModelixFeature::class, UnstableModelixFeature::class)

package org.modelix.mps.sync.plugin.automatic

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ChildLinkFromName
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.mps.sync.SyncServiceImpl
import org.modelix.mps.sync.mps.services.ServiceLocator
import org.modelix.mps.sync.plugin.ModelSyncService
import org.modelix.mps.sync.plugin.configuration.AutomaticModeConfig
import org.modelix.mps.sync.plugin.util.runRead
import org.modelix.mps.sync.transformation.modelixToMps.initial.ITreeToSTreeTransformer

private val moduleConcept = BuiltinLanguages.MPSRepositoryConcepts.Module

// TODO Olekz should not be here.
// TODO Olekz Might not be needed. (aka. just check type)
private val modulesInRootChildLink = ChildLinkFromName("modules")

private val LOG = logger<AutomaticSyncService>()

@Service(Service.Level.PROJECT)
class AutomaticSyncService(val project: Project) {

    private lateinit var client: ModelClientV2
    private lateinit var config: AutomaticModeConfig
    private val serviceLocator: ServiceLocator
        get() = project.service()

    private val modelSyncService: ModelSyncService
        get() = project.service()

    private val syncService: SyncServiceImpl
        get() = serviceLocator.syncService

    private val networkDispatcher
        get() = serviceLocator.networkDispatcher

    private val mpsProject
        get() = serviceLocator.mpsProject

    // TODO Olekz add listener
    fun setupAutomaticSync(providedConfig: AutomaticModeConfig) {
        config = providedConfig
        LOG.info("Setting up automatic synchronization. Using configuration `$config`.")
        val createdClient = modelSyncService.connectModelServer(config.modelServerUrl.toExternalForm(), config.jwt)
        checkNotNull(createdClient) {
            "Failed to setup automatic synchronization. Model client could not be created."
        }
        client = createdClient

        val localVersion = pullOrCreateBranch(client, config.branch)
        val replicatedModel = syncService.setReplicatedModel(client, config.branch, localVersion)
        val branch = replicatedModel.getBranch()
        if (localVersion.baseVersion != null) {
            val modulesInMps = mpsProject.runRead { mpsProject.projectModules }
            val moduleIdsOnServer = branch.computeRead {
                val modulesOnServer = branch.getRootNode().getChildren(modulesInRootChildLink)
                modulesOnServer.map { moduleChild ->
                    val moduleIdOnServer = moduleChild.getPropertyValue(moduleConcept.id)
                    checkNotNull(moduleIdOnServer) { "Module ID should always exist." }
                }
            }
            val bindings = moduleIdsOnServer.map { moduleIdOnServer ->
//                if (moduleIdOnServer == "61f6ca84-d13b-409d-8cc9-522f39d2cbf5") {
//                    return@map emptyList()
//                }
                // TODO Olekz check bindings
                val bindings = ITreeToSTreeTransformer(
                    replicatedModel.getBranch(),
                    serviceLocator.languageRepository,
                    serviceLocator,
                )
                    .transform(moduleIdOnServer)
                // TODO Olekz assert bindings
                bindings
            }
            bindings.forEach {
                it.forEach { it.activate() }
            }

            runBlocking(networkDispatcher) {
                replicatedModel.start()
            }

            // TODO Olekz test sync aka. activate replication
            // TODO Olekz test
            // TODO Olekz bindings
//            TODO("Not implemented")
//            bindModuleFromServer
            // TODO Olekz
//            updateMpsData(version)
//            modelSyncService.rebindModules(client, config.branch, localVersion, mpsProject.repository.modules.map { it as AbstractModule })
        }
        // TODO comment why we are doing this after start
        // TODO make the workspace the single source of truth
//        if (localVersion.baseVersion == null) {
//            runBlocking(networkDispatcher) {
//                replicatedModel.start()
//            }
//            // TODO Olekz stage 2 cleanup
//            val modules = mpsProject.runRead { mpsProject.projectModules }
//            for (module in modules) {
//                LOG.info("Binding `${module} from MPS to model server.")
//                check(module is AbstractModule) {
//                    // TODO Olekz Should not required to be an AbstractModule.
//                    // Should only require SModule.
//                    "$module be must be a ${AbstractModule::class}."
//                }
//                // TODO Olekz bind multiple
//                val bindings = modelSyncService.bindModuleFromMps(module, branch)
//                // TODO Olekz check bindings
//                // TODO Olekz test replication
// //                bindings.forEach { it.activate() }
//            }
//        }
    }

    private fun pullOrCreateBranch(client: ModelClientV2, branch: BranchReference) =
        runBlocking(networkDispatcher) {
            val repositories = client.listRepositories()
            if (repositories.contains(branch.repositoryId)) {
                // XXX In rare cases, the repository might get deleted after we checked its existence.
                LOG.info("Pulling branch `${branch.branchName}`.")
                val version = client.pullIfExists(branch)
                checkNotNull(version) {
                    // When the repository exists, but the branch does not,
                    // we do not know from which branch to create the needed branch.
                    "Failed to setup automatic synchronization. Repository `${branch.repositoryId.id}` exists but does not contain branch `${branch.branchName}`."
                }
                return@runBlocking version as CLVersion
            } else {
                LOG.info("Creating repository `${branch.repositoryId.id}`.")
                // XXX In rare cases, the repository might get created after we checked its existence.
                val initialVersion = client.initRepository(branch.repositoryId)
                LOG.info("Creating branch `${branch.branchName}`.")
                // Do not return `initialVersion` because in the meantime some could have pushed to the branch.
                return@runBlocking client.push(branch, initialVersion, initialVersion) as CLVersion
            }
        }
}

//        LOG.info("Setting up automatic synchronization. Using configuration $pluginMode")
//        val modelSyncService: ModelSyncService = project.service()
//        val client = modelSyncService.connectModelServer(pluginMode.modelServerUrl.toExternalForm(), pluginMode.initialJwt)
//        checkNotNull(client) {
//            "Could not connect to client."
//        }
//
//        runBlocking {
//            val repositoryId = pluginMode.branch.repositoryId
//            if (!client.listRepositories().contains(pluginMode.branch.repositoryId)) {
//                client.initRepository(repositoryId)
//            }
//        }
//        val branch = modelSyncService.connectToBranch(client, pluginMode.branch)
//        checkNotNull(branch) {
//            "Could not connect to branch."
//        }
//        val serviceLocator: ServiceLocator = project.service()
//        val projectLifecycleTracker = serviceLocator.projectLifecycleTracker
//        val bindingsRegistry = serviceLocator.bindingsRegistry
//        val nodeSynchronizer = NodeSynchronizer(branch, serviceLocator = serviceLocator)
//        val syncService = serviceLocator.syncService
//
//        val mpsProject =  project.toMpsProject()
//
//        mpsProject.repository.modules.forEach { module ->
//            val abstractModule = module as AbstractModule
//            syncService.bindModuleFromMps(abstractModule, branch)
//            val bindings = syncService.bindModuleFromMps(abstractModule, branch)
//            bindings.forEach { it.activate() }
//        }
//
//        mpsProject.repository.addRepositoryListener(object: SRepositoryListener {
//            override fun moduleAdded(module: SModule) {
//                val abstractModule = module as AbstractModule
//                val bindings = syncService.bindModuleFromMps(abstractModule, branch)
//                bindings.forEach { it.activate() }
//            }
//
//            override fun moduleRemoved(moduleReference: SModuleReference) {
//                if (projectLifecycleTracker.projectClosing) {
//                    return
//                }
//
//                val binding = bindingsRegistry.getModuleBindings().find { it.module.moduleId == moduleReference.moduleId }
//                if (binding != null) {
//                    nodeSynchronizer.removeNode(
//                        parentNodeIdProducer = { ITree.ROOT_ID },
//                        childNodeIdProducer = { it[moduleReference.moduleId]!! },
//                    )
//                    binding.deactivate(removeFromServer = true)
//                }
//            }
//        })
