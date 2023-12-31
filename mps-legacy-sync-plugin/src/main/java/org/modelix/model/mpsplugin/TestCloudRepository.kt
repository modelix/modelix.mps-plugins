package org.modelix.model.mpsplugin

import org.modelix.model.api.IBranch
import org.modelix.model.client.IIndirectBranch
import org.modelix.model.lazy.RepositoryId

/*Generated by MPS */
class TestCloudRepository(override val branch: IBranch) : ICloudRepository {
    override fun completeId(): String {
        return "test"
    }

    override val activeBranch: IIndirectBranch
        get() {
            return NonIndirectBranch(branch)
        }

    override val repositoryId: RepositoryId
        get() {
            return RepositoryId("test")
        }
}
