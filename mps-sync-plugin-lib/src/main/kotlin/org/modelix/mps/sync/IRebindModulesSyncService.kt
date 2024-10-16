package org.modelix.mps.sync

import jetbrains.mps.project.AbstractModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import java.io.IOException

/**
 * A sub-interface of [ISyncService] that helps with connecting to the model server and rebinding local MPS Modules to
 * a modelix branch.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
interface IRebindModulesSyncService {

    /**
     * Creates a connection to the model server. Do not forget to close the created client after use.
     *
     * @param serverURL the model server URL.
     * @param jwt the JWT auth token.
     *
     * @throws IOException may throw the exception if a connection error occurred. Alternatively the method may return
     * null.
     *
     * @return the model client that is connected to the model server or null if a connection error occurred.
     */
    @Throws(IOException::class)
    fun connectModelServer(serverURL: String, jwt: String? = null): ModelClientV2?

    /**
     * Connects to the model server's specific branch's specific version, and creates [IBinding]s for the modules and
     * their models. So that changes in the respective modules and models will be reflected on the model server.
     *
     * @param client the model server connection.
     * @param branchReference to which branch we want to connect.
     * @param initialVersion which version in the branch history we want to use.
     * @param modules for which MPS modules and their models we want to create [IBinding]s.
     *
     * @return the [IBinding]s that are created for the modules and their models.
     */
    fun rebindModules(
        client: ModelClientV2,
        branchReference: BranchReference,
        initialVersion: CLVersion,
        modules: Iterable<AbstractModule>,
    ): Iterable<IBinding>?
}
