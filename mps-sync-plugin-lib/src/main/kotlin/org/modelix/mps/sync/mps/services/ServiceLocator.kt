package org.modelix.mps.sync.mps.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.ILanguageRepository
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.SyncServiceImpl
import org.modelix.mps.sync.bindings.BindingsRegistry
import org.modelix.mps.sync.modelix.branch.BranchRegistry
import org.modelix.mps.sync.mps.notifications.WrappedNotifier
import org.modelix.mps.sync.mps.util.toMpsProject
import org.modelix.mps.sync.tasks.FuturesWaitQueue
import org.modelix.mps.sync.tasks.SyncQueue
import org.modelix.mps.sync.transformation.cache.MpsToModelixMap

/**
 * A collector class to simplify injecting the commonly used services in the sync plugin. In this way, we do not have to
 * worry about the initialization sequence of the services.
 *
 * Add your class here, whose lifecycle you would like to bind to the recently opened [Project], and if you want to use
 * it from other (service) classes. The class must implement the [InjectableService] interface so that it can be
 * initialized and disposed according to the [Project]'s lifecycle.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Service(Service.Level.PROJECT)
class ServiceLocator(val project: Project) : Disposable {

    /**
     * The entry class of the synchronization workflows.
     */
    val syncService = SyncServiceImpl()

    /**
     * The task queue of the sync plugin.
     */
    val syncQueue = SyncQueue()

    /**
     * The registry to store the [IBinding]s.
     */
    val bindingsRegistry = BindingsRegistry()

    /**
     * A registry to store the modelix [IBranch] we are connected to.
     */
    val branchRegistry = BranchRegistry()

    /**
     * The lookup map (internal cache) between the MPS elements and the corresponding modelix Nodes.
     */
    val nodeMap = MpsToModelixMap()

    /**
     * A notifier that can notify the user about certain messages in a nicer way than just simply logging the message.
     */
    val wrappedNotifier = WrappedNotifier()

    /**
     * Tracks the active [Project]'s lifecycle.
     */
    val projectLifecycleTracker = ProjectLifecycleTracker()

    /**
     * The Futures queue of the sync plugin.
     */
    val futuresWaitQueue = FuturesWaitQueue()

    /**
     * The [jetbrains.mps.project.MPSProject] that is open in the active MPS window.
     */
    val mpsProject = project.toMpsProject()

    /**
     * The active [SRepository] to access the [SModel]s and [SModule]s in MPS.
     */
    val mpsRepository: SRepository
        get() = mpsProject.repository

    /**
     * The [ILanguageRepository] that can resolve Concept UIDs of modelix nodes to Concepts in MPS.
     */
    val languageRepository = ApplicationManager.getApplication()
        .getService(MPSLanguageRepositoryProvider::class.java).mpsLanguageRepository

    /**
     * The services that are registered in the service locator.
     */
    private val services: Set<InjectableService> = setOf(
        syncService,
        syncQueue,
        bindingsRegistry,
        branchRegistry,
        nodeMap,
        wrappedNotifier,
        projectLifecycleTracker,
        futuresWaitQueue,
    )

    init {
        services.forEach { it.initService(this) }
    }

    /**
     * Dispose the registered [services].
     *
     * @see [InjectableService.dispose].
     */
    override fun dispose() = services.forEach(InjectableService::dispose)
}
