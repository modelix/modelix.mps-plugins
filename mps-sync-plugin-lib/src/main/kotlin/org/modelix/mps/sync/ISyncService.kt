package org.modelix.mps.sync

import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.project.AbstractModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.lazy.BranchReference
import org.modelix.mps.sync.bindings.ModelBinding
import org.modelix.mps.sync.mps.util.ModuleIdWithName
import java.io.IOException
import java.net.URL
import java.util.concurrent.CompletableFuture

/**
 * The interface of the synchronization coordinator class, that can connect to the model server and bind models and
 * modules in both directions, starting from the model server or from MPS as well.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
interface ISyncService : IRebindModulesSyncService {

    /**
     * @throws IOException if a connection error occurred.
     *
     * @see [IRebindModulesSyncService.connectModelServer], but with a non-null return value.
     */
    @Throws(IOException::class)
    override fun connectModelServer(serverURL: String, jwt: String?) = connectModelServer(URL(serverURL), jwt)

    /**
     * @throws IOException if a connection error occurred.
     *
     * @see [IRebindModulesSyncService.connectModelServer], but with a non-null return value.
     */
    @Throws(IOException::class)
    fun connectModelServer(serverURL: URL, jwt: String? = null): ModelClientV2

    /**
     * Disconnects the [client] from the model server.
     *
     * @param client the client to disconnect from the model server.
     */
    fun disconnectModelServer(client: ModelClientV2)

    /**
     * Connects the branch identified by its [branchReference], using the [client] model client.
     *
     * @param client the model client to use for the connection.
     * @param branchReference the identifier of the branch to connect to.
     *
     * @return a reference for the connected branch.
     */
    fun connectToBranch(client: ModelClientV2, branchReference: BranchReference): IBranch

    /**
     * Disconnects the [branch] that is called [branchName] from the model server.
     *
     * @param branch the branch to disconnect from the model server.
     * @param branchName the name of the branch.
     */
    fun disconnectFromBranch(branch: IBranch, branchName: String)

    /**
     * @return the active branch we are connected to, or null.
     */
    fun getActiveBranch(): IBranch?

    /**
     * Binds a Module and the transitively reachable Modules, Models and Nodes to MPS. I.e. it downloads and transforms
     * the modelix nodes to the corresponding MPS elements and finally establishes [IBinding]s between the model server
     * and MPS for the synchronized Modules and Models.
     *
     * @param client the model client to be used for the connection.
     * @param branchReference the identifier of the branch from which we download the Modules, Models and Nodes.
     * @param module the ID and the name of the Module that is the starting point of the transformation.
     *
     * @return an [Iterable] of the [IBinding]s that were created for the synchronized Modules and Models.
     */
    fun bindModuleFromServer(
        client: ModelClientV2,
        branchReference: BranchReference,
        module: ModuleIdWithName,
    ): Iterable<IBinding>

    /**
     * Synchronizes the local MPS [module] to the modelix [branch].
     *
     * @param module the MPS Module to synchronize to the model server.
     * @param branch the modelix branch to which we want to synchronize the [module].
     *
     * @return the [IBinding]s that were created for the synchronized Modules and Models.
     */
    fun bindModuleFromMps(module: AbstractModule, branch: IBranch): Iterable<IBinding>

    /**
     * Synchronizes the local MPS [model] to the modelix [branch].
     *
     * @param model the MPS Model to synchronize to the model server.
     * @param branch the modelix branch to which we want to synchronize the [model].
     *
     * @return the [ModelBinding] that was created for the synchronized Model.
     */
    fun bindModelFromMps(model: SModelBase, branch: IBranch): IBinding
}

/**
 * Represents a binding between an MPS element an a modelix element.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
interface IBinding {

    /**
     * Activates the binding and then runs the [callback] [Runnable], unless it is null.
     *
     * @param callback the [Runnable] to run after the binding activation.
     */
    fun activate(callback: Runnable? = null)

    /**
     * Deactivates the binding and then runs the [callback] [Runnable], unless it is null.
     *
     * @param removeFromServer if true then the modelix element has to be removed from the server.
     * @param callback the [Runnable] to run after the binding activation.
     *
     * @return a [CompletableFuture] so that we do not have to synchronously wait for the completion of this method.
     */
    fun deactivate(removeFromServer: Boolean, callback: Runnable? = null): CompletableFuture<Any?>

    /**
     * @return the name of the binding.
     */
    fun name(): String
}
