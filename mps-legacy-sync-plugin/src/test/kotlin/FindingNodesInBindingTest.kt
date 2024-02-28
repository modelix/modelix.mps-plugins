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
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.PNodeReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsplugin.ModelCloudImportUtils
import org.modelix.model.mpsplugin.ModelServerConnections

class FindingNodesInBindingTest : SyncPluginTestBase("projectWithOneEmptyModel") {

    fun `test can find cloud nodes and MPS nodes`() = runTestWithSyncService { syncService ->
        // Arrange: Create data
        val classConcept = resolveMPSConcept("jetbrains.mps.baseLanguage.ClassConcept")
        val existingSolution = mpsProject.projectModules.single()
        // Arrange: Initial sync to the server
        val server = syncService.connectServer(httpClient, Url("http://localhost/"))
        server.newBranchConnection(RepositoryId("testRepository1").getBranchReference())
            .bindModule(existingSolution, null).flush()
        server.newBranchConnection(RepositoryId("testRepository2").getBranchReference())
            .bindModule(existingSolution, null).flush()

        // Act
        val newRootNode: SNode = writeAction {
            val model = existingSolution.modelsWithoutDescriptor().single()
            val rootNode = model.createNode(classConcept as SConcept)
            model.addRootNode(rootNode)
            return@writeAction rootNode
        }
        // `flush` should not be called in this test after creating a root node,
        // because `findMpsNode` and `findCloudNodeReference` call flush internally.
        val cloudNodes = syncService.findCloudNodeReference(newRootNode)
        val mpsNodeFromFirstCloudNode = syncService.findMpsNode(cloudNodes[0].reference).single()
        val mpsNodeFromFirstSecondCloudNode = syncService.findMpsNode(cloudNodes[1].reference).single()

        // Assert
        assertEquals(2, cloudNodes.size)
        assertEquals(newRootNode, mpsNodeFromFirstSecondCloudNode)
        assertEquals(mpsNodeFromFirstCloudNode, mpsNodeFromFirstSecondCloudNode)
    }

    fun `test can not find cloud node and MPS node`() = runTestWithSyncService { syncService ->
        // Arrange: Create data
        val classConcept = resolveMPSConcept("jetbrains.mps.baseLanguage.ClassConcept")
        val existingSolution = mpsProject.projectModules.single()
        var notAddedRood: SNode? = null
        readAction {
            val model = existingSolution.modelsWithoutDescriptor().single()
            notAddedRood = model.createNode(classConcept as SConcept)
        }
        // Arrange: Initial sync to the server
        val server = syncService.connectServer(httpClient, Url("http://localhost/"))
        server.newBranchConnection(defaultBranchRef)
            .bindModule(existingSolution, null)
            .flush()

        // Act
        val cloudNodes = syncService.findCloudNodeReference(notAddedRood!!)
        val mpsNodes = syncService.findMpsNode(PNodeReference(999L, defaultBranchRef.branchName))

        // Assert
        assertEmpty(cloudNodes)
        assertEmpty(mpsNodes)
    }

    fun `test can find cloud nodes and MPS nodes when connection by legacy API`() = runTestWithSyncService { syncService ->
        // Using the legacy API ModelCloudImportUtils.copyAndSyncInModelixAsIndependentModule
        // instead of IBranchConnection.bindModule.
        // The legacy API must be supported as it is used in actions like CopyAndSyncPhysicalModuleOnCloud_Action

        // Arrange: Create data
        val classConcept = resolveMPSConcept("jetbrains.mps.baseLanguage.ClassConcept")
        val existingSolution = mpsProject.projectModules.single()

        // Arrange: Connect to server
        syncService.connectServer(httpClient, Url("http://localhost/"))
        val modelServer = ModelServerConnections.instance.modelServers.single()

        // Arrange: Setup bindings
        // after `info` is fetched, we can start adding repositories
        delayUntil { modelServer.info != null }
        val firstRepositoryId = "testRepository1"
        val secondRepositoryId = "testRepository2"
        writeAction {
            modelServer.addRepository(firstRepositoryId)
            modelServer.addRepository(secondRepositoryId)
        }
        delayUntil {
            ModelServerConnections.instance.connectedTreesInRepositories
                .map { it.branch.getId() }
                .containsAll(listOf(firstRepositoryId, secondRepositoryId))
        }
        val firstRepositoryTree = ModelServerConnections.instance.connectedTreesInRepositories
            .single { it.branch.getId() == firstRepositoryId }
        val secondRepositoryTree = ModelServerConnections.instance.connectedTreesInRepositories
            .single { it.branch.getId() == secondRepositoryId }
        writeAction {
            ModelCloudImportUtils.copyAndSyncInModelixAsIndependentModule(firstRepositoryTree, existingSolution, project, null)
            ModelCloudImportUtils.copyAndSyncInModelixAsIndependentModule(secondRepositoryTree, existingSolution, project, null)
        }

        // Act
        val newRootNode: SNode = writeAction {
            val model = existingSolution.modelsWithoutDescriptor().single()
            val rootNode = model.createNode(classConcept as SConcept)
            model.addRootNode(rootNode)
            return@writeAction rootNode
        }
        val cloudNodes = syncService.findCloudNodeReference(newRootNode)
        val mpsNodeFromFirstCloudNode = syncService.findMpsNode(cloudNodes[0].reference).single()
        val mpsNodeFromFirstSecondCloudNode = syncService.findMpsNode(cloudNodes[1].reference).single()

        // Assert
        assertEquals(2, cloudNodes.size)
        assertEquals(newRootNode, mpsNodeFromFirstSecondCloudNode)
        assertEquals(mpsNodeFromFirstCloudNode, mpsNodeFromFirstSecondCloudNode)
    }
}
