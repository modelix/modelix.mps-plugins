package org.modelix.mps.sync

import com.intellij.openapi.project.Project
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.client2.getReplicatedModel
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.modelix.ModelixBranchListener
import org.modelix.mps.sync.modelix.ReplicatedModelRegistry
import org.modelix.mps.sync.mps.ActiveMpsProjectInjector
import org.modelix.mps.sync.mps.RepositoryChangeListener
import org.modelix.mps.sync.tasks.FuturesWaitQueue
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.modelixToMps.initial.ITreeToSTreeTransformer
import java.net.URL

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class SyncServiceImpl : SyncService {

    private val logger = KotlinLogging.logger {}
    private val mpsProjectInjector = ActiveMpsProjectInjector

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val replicatedModelByBranchReference = mutableMapOf<BranchReference, ReplicatedModel>()
    private val changeListenerByReplicatedModel = mutableMapOf<ReplicatedModel, IBranchListener>()

    private var projectWithChangeListener: Pair<MPSProject, RepositoryChangeListener>? = null

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
        logger.info { "Connecting to $serverURL" }
        // TODO: use JWT here
        val modelClientV2 = ModelClientV2.builder().url(serverURL.toString()).build()
        modelClientV2.init()
        logger.info { "Connection to $serverURL successful" }

        callback?.invoke()

        return modelClientV2
    }

    override fun disconnectModelServer(
        client: ModelClientV2,
        callback: (() -> Unit)?,
    ) {
        // TODO what shall happen with the bindings if we disconnect from model server?
        client.close()
        callback?.invoke()
    }

    override suspend fun bindModule(
        client: ModelClientV2,
        branchReference: BranchReference,
        moduleId: String,
        callback: (() -> Unit)?,
    ): Iterable<IBinding> {
        // fetch replicated model and branch content
        // TODO how to handle multiple replicated models at the same time?
        val replicatedModel =
            replicatedModelByBranchReference.getOrDefault(branchReference, client.getReplicatedModel(branchReference))
        val replicateModelIsAlreadySynched = replicatedModelByBranchReference.containsKey(branchReference)

        /*
         * TODO fixme:
         * (1) How to propagate replicated model to other places of code?
         * (2) How to know to which replicated model we want to upload? (E.g. when connecting to multiple model servers?)
         * (3) How to replace the outdated replicated models that are already used from the registry?
         *
         * Possible answers:
         * (1) via the registry
         * (2) Base the selection on the parent project and the active model server connections we have. E.g. let the user select to which model server they want to upload the changes and so they get the corresponding replicated model.
         * (3) We don't. We have to make sure that the places always have the latest replicated models from the registry. E.g. if we disconnect from the model server then we remove the replicated model (and thus break the registered event handlers), otherwise the event handlers as for the replicated model from the registry (based on some identifying metainfo for example, so to know which replicated model they need).
         */
        ReplicatedModelRegistry.model = replicatedModel
        replicatedModelByBranchReference[branchReference] = replicatedModel

        // TODO when and how to dispose the replicated model and everything that depends on it?
        val branch = if (replicateModelIsAlreadySynched) {
            replicatedModel.getBranch()
        } else {
            replicatedModel.start()
        }

        // transform the model
        val targetProject = mpsProjectInjector.activeMpsProject!!
        val languageRepository = registerLanguages(targetProject)
        val bindings = ITreeToSTreeTransformer(branch, languageRepository).transform(moduleId)

        // register replicated model change listener
        if (!replicateModelIsAlreadySynched) {
            val listener = ModelixBranchListener(replicatedModel, languageRepository, branch)
            branch.addListener(listener)
            changeListenerByReplicatedModel[replicatedModel] = listener
        }

        // register MPS project change listener
        if (projectWithChangeListener == null) {
            val repositoryChangeListener = RepositoryChangeListener(branch)
            targetProject.repository.addRepositoryListener(repositoryChangeListener)
            projectWithChangeListener = Pair(targetProject, repositoryChangeListener)
        }

        // trigger callback after activation
        callback?.invoke()

        return bindings
    }

    override fun setActiveProject(project: Project) {
        mpsProjectInjector.setActiveProject(project)
    }

    override fun dispose() {
        // cancel all running coroutines
        coroutineScope.cancel()
        SyncQueue.close()
        FuturesWaitQueue.close()
        // unregister change listeners
        resetProjectWithChangeListener()
        changeListenerByReplicatedModel.forEach { it.key.getBranch().removeListener(it.value) }
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

    private fun resetProjectWithChangeListener() {
        projectWithChangeListener?.let {
            val project = it.first
            val listener = it.second
            project.repository.removeRepositoryListener(listener)
            projectWithChangeListener = null
        }
    }
}
