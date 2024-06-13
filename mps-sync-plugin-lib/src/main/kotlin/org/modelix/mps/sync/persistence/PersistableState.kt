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
 *
 * @property clientUrl the URL of the model server to which we were connected
 * @property repositoryId the ID of the modelix repository to which we were connected
 * @property branchName the name of the branch in the modelix branch
 * @property localVersion the latest version hash we know from the branch
 * @property moduleIds the IDs of the Modules that were synchronized
 */
data class PersistableState(
    // modelix connection
    var clientUrl: String = "",
    var repositoryId: String = "",
    var branchName: String = "",
    var localVersion: String = "",

    // synchronized modules
    var moduleIds: List<String> = listOf(),
) {

    private val logger = KotlinLogging.logger {}
    private val notifier = InjectableNotifierWrapper

    /**
     * Initializes the [PersistableState]'s fields with values fetched from the internal state of the modelix sync lib.
     * The method overrides the values of the [this]' fields.
     *
     * @return this with initialized fields, or the original state if [BranchRegistry.model] is null
     */
    fun fetchState(): PersistableState {
        val replicatedModel = BranchRegistry.model
        if (replicatedModel == null) {
            logger.warn { "Replicated Model is null, therefore an empty PersistableState will be saved." }
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

        return this
    }

    /**
     * Restores the internal state of the modelix sync lib via [syncService], in the following order:
     *
     * 1. connects to the model server denoted by [PersistableState.clientUrl],
     * 2. loads the specific version ([PersistableState.localVersion]) of the [PersistableState.repositoryId],
     * 3. connects to the branch ([PersistableState.branchName])
     * 4. creates bindings for the synchronized modules ([PersistableState.moduleIds])
     *
     * @param syncService the interface to the modelix sync lib to restore its state
     *
     * @return some context statistics about the restored state
     */
    fun restoreState(syncService: IRebindModulesSyncService): RestoredStateContext? {
        var client: ModelClientV2? = null

        try {
            if (clientUrl.isBlank() || repositoryId.isBlank() || branchName.isBlank() || localVersion.isBlank()) {
                logger.debug { "Saved client URL, Repository ID, branch name or local version is empty, thus skipping PersistableState restoration." }
                return null
            }

            if (moduleIds.isEmpty()) {
                logger.debug { "List of restorable Modules is empty, thus skipping PersistableState restoration." }
                return null
            }

            logger.debug { "Restoring connection to model server." }
            client = syncService.connectModelServer(clientUrl, "")
            if (client == null) {
                throw IllegalStateException("Connection to $clientUrl failed, thus PersistableState is not restored.")
            }

            val repositoryId = RepositoryId(repositoryId)
            var initialVersion: CLVersion
            runBlocking {
                initialVersion = client.loadVersion(repositoryId, localVersion, null) as CLVersion
            }
            logger.debug { "Connection to model server is restored." }

            val branchReference = BranchReference(repositoryId, branchName)
            var modules = listOf<AbstractModule>()
            var bindings: Iterable<IBinding>? = null
            ActiveMpsProjectInjector.runMpsReadAction { repository ->
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
                bindings = syncService.rebindModules(client, branchReference, initialVersion, modules)
            }

            if (bindings == null) {
                throw IllegalStateException("Rebinding modules failed, thus PersistableState is not restored.")
            }

            logger.debug { "Bindings are recreated, now activating them." }
            bindings!!.forEach(IBinding::activate)
            logger.debug { "Bindings are activated." }
            return RestoredStateContext(client, branchReference, modules)
        } catch (t: Throwable) {
            val message =
                "Error occurred, while restoring persisted state. Connection to model server, bindings and synchronization cache might not be established and activated. Please check logs for details."
            notifier.notifyAndLogError(message, t, logger)

            close(client, MpsToModelixMap)

            return null
        }
    }

    private fun close(client: ModelClientV2?, cache: MpsToModelixMap) {
        client?.let {
            it.close()
            val message = "Disconnected from server: ${it.baseUrl}"
            notifier.notifyAndLogInfo(message, logger)
        }
        cache.clear()
    }
}

/**
 * Some context after the [PersistableState] has restored the sync plugin's internal state.
 *
 * @property modelClient the client that is connected to the model server
 * @property branchReference the branch in the repository to which we are connected
 * @property modules the MPS modules for which we established the bindings
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class RestoredStateContext(
    val modelClient: ModelClientV2,
    val branchReference: BranchReference,
    val modules: List<AbstractModule>,
)
