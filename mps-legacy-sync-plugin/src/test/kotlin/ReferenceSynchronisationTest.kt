/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import io.ktor.http.Url
import org.modelix.model.api.PNodeReference
import org.modelix.model.data.NodeData
import org.modelix.model.lazy.filterNotNullValues
import org.modelix.model.mpsplugin.ModelCloudImportUtils
import org.modelix.model.mpsplugin.ModelServerConnections

class ReferenceSynchronisationTest : SyncPluginTestBase("projectWithReferences") {

    private fun assertAllReferencesArePNodeReferences(nodeData: NodeData) {
        for ((referenceRole, referenceValue) in nodeData.references) {
            val reference = PNodeReference.tryDeserialize(referenceValue)
            assertNotNull(
                "Reference is not a PNodeReference. [node=${nodeData.id}] [role=$referenceRole] [reference=$referenceValue]",
                reference,
            )
        }
        nodeData.children.forEach(::assertAllReferencesArePNodeReferences)
    }

    private fun countSetReferences(nodeData: NodeData): Int =
        nodeData.references.filterNotNullValues().size + nodeData.children.map(::countSetReferences).sum()

    private fun assertNumberOfSetReferences(expectedNumberOfSetReferences: Int, nodeData: NodeData) {
        val actualNumberSetOfReferences = countSetReferences(nodeData)
        assertEquals(expectedNumberOfSetReferences, actualNumberSetOfReferences)
    }

    fun `test references are initially synced as  modelix references in module`() =
        runTestWithSyncService { syncService ->
            // Arrange
            val existingSolution = mpsProject.projectModules.single()

            // Act
            val moduleBinding = syncService.connectServer(httpClient, Url("http://localhost/"))
                .newBranchConnection(defaultBranchRef)
                .bindModule(existingSolution, null)
            moduleBinding.flush()

            // Assert
            val rootNodeData = readDumpFromServer(defaultBranchRef)
            // The model has exactly two references.
            assertNumberOfSetReferences(2, rootNodeData)
            assertAllReferencesArePNodeReferences(rootNodeData)
        }

    fun `test references are initially synced as modelix references in module when connecting by legacy API`() =
        runTestWithSyncService { syncService ->
            // Using the legacy API ModelCloudImportUtils.copyAndSyncInModelixAsIndependentModule
            // instead of IBranchConnection.bindModule.
            // The legacy API must be supported as it is used in actions like CopyAndSyncPhysicalModuleOnCloud_Action

            // Arrange
            val existingSolution = mpsProject.projectModules.single()
            syncService.connectServer(httpClient, Url("http://localhost/"))
            val modelServer = ModelServerConnections.instance.modelServers.single()
            // Arrange: Setup bindings
            // after `info` is fetched, we can start adding repositories
            delayUntil { modelServer.info != null }
            delayUntil {
                ModelServerConnections.instance.connectedTreesInRepositories
                    .map { it.branch.getId() }
                    .containsAll(listOf(defaultBranchRef.repositoryId.id))
            }
            val repositoryTree = ModelServerConnections.instance.connectedTreesInRepositories
                .single { it.branch.getId() == defaultBranchRef.repositoryId.id }

            // Act
            writeAction {
                ModelCloudImportUtils.copyAndSyncInModelixAsIndependentModule(
                    repositoryTree,
                    existingSolution,
                    project,
                    null,
                )
            }
            delayUntil(exceptionMessage = "Failed to sync module to model server") {
                val dataOnServer = readDumpFromServer(defaultBranchRef)
                dataOnServer.children.any { module ->
                    module.children.any { it.role == "models" }
                }
            }

            // Assert
            val rootNodeData = readDumpFromServer(defaultBranchRef)
            // The model has exactly two references.
            assertNumberOfSetReferences(2, rootNodeData)
            assertAllReferencesArePNodeReferences(rootNodeData)
        }
}
