package org.modelix.mps.sync.mps.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.modelix.kotlin.utils.UnstableModelixFeature
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

    val syncService = SyncServiceImpl()
    val syncQueue = SyncQueue()
    val bindingsRegistry = BindingsRegistry()
    val branchRegistry = BranchRegistry()
    val nodeMap = MpsToModelixMap()

    val wrappedNotifier = WrappedNotifier()
    val projectLifecycleTracker = ProjectLifecycleTracker()
    val futuresWaitQueue = FuturesWaitQueue()

    val mpsProject = project.toMpsProject()

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

    override fun dispose() = services.forEach(InjectableService::dispose)
}
