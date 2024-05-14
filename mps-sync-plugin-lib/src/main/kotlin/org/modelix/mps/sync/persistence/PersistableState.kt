package org.modelix.mps.sync.persistence

import jetbrains.mps.project.AbstractModule
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.IRebindModulesSyncService
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.modelix.BranchRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.notifications.InjectableNotifierWrapper
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
/**
 * States are capable of taking a snapshot of the current bindings to modelix servers with getCurrentState(),
 * and recreating that state by calling load(). Note that currently the bindings of the state will be added during
 * load(), but not included bindings will be disconnected.
 *
 * States will be automatically saved when a project is closed, and automatically loaded, when that project is
 * reopened. Technically, it should also be possible to create a state yourself in order to load it, although this
 * is not what they were made for.
 *
 * WARNING:
 * About the State class:
 * - do not make it an inner class, otherwise deserialization will fail with an exception
 * About the State's fields:
 * - Mutable collections will not be persisted!!!
 * - Maps and Collections will only be persisted 2 layers deep (List<List<String>> works, but List<List<List<String>>> not)
 * - Pairs will not be persisted
 */
data class PersistableState(
    // modelix connection
    var clientUrl: String = "",
    var repositoryId: String = "",
    var branchName: String = "",
    var localVersion: String = "",

    // synchronized modules
    var moduleIds: List<String> = listOf(),

    // MPS to Modelix mapping
    var synchronizationCache: String = "",
) {

    private val logger = KotlinLogging.logger {}
    private val notifier = InjectableNotifierWrapper

    fun fetchState(): PersistableState {
        val replicatedModel = BranchRegistry.model
        if (replicatedModel == null) {
            logger.warn { "Replicated Model is null, therefore an empty State will be saved." }
            return this
        }

        clientUrl = (replicatedModel.client as ModelClientV2).baseUrl
        repositoryId = replicatedModel.branchRef.repositoryId.id
        branchName = replicatedModel.branchRef.branchName

        runBlocking {
            localVersion = replicatedModel.getCurrentVersion().getContentHash()
        }

        BindingsRegistry.getModuleBindings().forEach {
            moduleIds += it.module.moduleId.toString()
        }

        ActiveMpsProjectInjector.runMpsReadActionBlocking {
            synchronizationCache = MpsToModelixMap.Serializer().serialize()
        }

        return this
    }

    fun restoreState(syncService: IRebindModulesSyncService): RestoredStateContext? {
        var client: ModelClientV2? = null

        try {
            if (clientUrl.isBlank() || repositoryId.isBlank() || branchName.isBlank() || localVersion.isBlank()) {
                logger.debug { "Saved client URL, Repository ID, branch name or local version is empty, thus skipping synchronization plugin state restoration." }
                return null
            }

            if (moduleIds.isEmpty()) {
                logger.debug { "List of restorable Modules is empty, thus skipping synchronization plugin state restoration." }
                return null
            }

            var cacheIsEmpty = synchronizationCache.isBlank()
            if (!cacheIsEmpty) {
                ActiveMpsProjectInjector.runMpsReadActionBlocking {
                    MpsToModelixMap.Serializer().deserialize(synchronizationCache)
                    cacheIsEmpty = MpsToModelixMap.isEmpty()
                    logger.debug { "Synchronization cache is restored." }
                }
            }

            if (cacheIsEmpty) {
                notifier.notifyAndLogWarning(
                    "Serialized synchronization cache is empty, thus cloud synchronization plugin state is not restored.",
                    logger,
                )
                return null
            }

            logger.debug { "Restoring connection to model server." }
            client = syncService.connectModelServer(clientUrl, "")
            if (client == null) {
                val exception =
                    IllegalStateException("Connection to $clientUrl failed, thus cloud synchronization plugin state is not restored.")
                notifier.notifyAndLogError(exception.message!!, exception, logger)
                return null
            }

            val repositoryId = RepositoryId(repositoryId)
            var initialVersion: CLVersion
            runBlocking {
                initialVersion = client.loadVersion(repositoryId, localVersion, null) as CLVersion
            }
            logger.debug { "Connection to model server is restored." }

            lateinit var branchReference: BranchReference
            lateinit var modules: List<AbstractModule>
            ActiveMpsProjectInjector.runMpsReadActionBlocking { repository ->
                logger.debug { "Restoring SModules." }
                modules = moduleIds.map {
                    val id = PersistenceFacade.getInstance().createModuleId(it)
                    val module = repository.getModule(id)
                    requireNotNull(module) { "Could not restore module from ID ($id)." }
                    require(module is AbstractModule) { "Module ($module) is not an AbstractModule." }
                    module
                }
                logger.debug { "${modules.count()} SModules are restored." }

                logger.debug { "Recreating Bindings." }
                branchReference = BranchReference(repositoryId, branchName)
                val bindings = syncService.rebindModules(client, branchReference, initialVersion, modules)
                bindings?.let {
                    logger.debug { "Bindings are recreated, now activating them." }
                    it.forEach(IBinding::activate)
                    logger.debug { "Bindings are activated." }
                }
            }

            return RestoredStateContext(client, repositoryId, branchReference, modules)
        } catch (t: Throwable) {
            val message =
                "Error occurred, while restoring persisted state. Connection to model server, bindings and synchronization cache might not be established and activated. Please check logs for details."
            notifier.notifyAndLogError(message, t, logger)

            client?.close()
            MpsToModelixMap.clear()

            return null
        }
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class RestoredStateContext(
    val modelClient: ModelClientV2,
    val repositoryId: RepositoryId,
    val branchReference: BranchReference,
    val modules: List<AbstractModule>,
)
