package org.modelix.mps.sync

import jetbrains.mps.project.AbstractModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import java.io.IOException

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
interface IRebindSyncService {

    @Throws(IOException::class)
    fun connectModelServer(serverURL: String, jwt: String? = null): ModelClientV2?

    fun rebindModules(
        client: ModelClientV2,
        branchReference: BranchReference,
        initialVersion: CLVersion,
        modules: Iterable<AbstractModule>,
    ): Iterable<IBinding>?
}
