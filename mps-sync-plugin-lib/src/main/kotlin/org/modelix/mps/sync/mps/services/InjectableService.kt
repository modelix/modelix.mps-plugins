package org.modelix.mps.sync.mps.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * Represents an injectable Service whose lifecycle is bound to a [Project]. I.e. the service will be initialized when
 * the [Project] is opened and disposed when it is closed.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
interface InjectableService : Disposable {
    /**
     * Use this method to initialize those fields of the class that get their instances from [ServiceLocator].
     *
     * @param serviceLocator the service locator from which we can ask for an instance of specific [InjectableService],
     * and whose [ServiceLocator.project] is the [Project] to whose lifecycle the Service is bound.
     */
    fun initService(serviceLocator: ServiceLocator) {}

    /**
     * Dispose the resources used by the Service.
     */
    override fun dispose() {}
}
