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

import org.modelix.model.client2.ModelClientV2
import org.modelix.model.mpsplugin.ModelServerConnections
import org.modelix.model.mpsplugin.plugin.EModelixExecutionMode
import org.modelix.model.mpsplugin.plugin.ModelixConfigurationSystemProperties

class ProjectorAutoBindingTest : SyncPluginTestBase("projectWithOneEmptyModel") {
    fun `test project binds project when in with execution mode is PROJECTOR`() {
        System.setProperty("MODEL_URI", "http://localhost")
        System.setProperty("REPOSITORY_ID", "default")
        System.setProperty(
            ModelixConfigurationSystemProperties.EXECUTION_MODE_SYSPROP,
            EModelixExecutionMode.PROJECTOR.toString(),
        )

        runTestWithSyncService {
            val modelClient = ModelClientV2.builder().url(baseUrl).client(httpClient).build()
            modelClient.init()
            delayUntil(exceptionMessage = "Failed to auto connect to the model server.") {
                ModelServerConnections.instance.modelServers.isNotEmpty()
            }
            assertTrue(ModelServerConnections.instance.modelServers.size == 1)
            delayUntil(exceptionMessage = "Failed to sync to model server.") {
                val branches = modelClient.listBranches(defaultBranchRef.repositoryId)
                branches.isNotEmpty()
            }

            val dataOnServer = readDumpFromServer(defaultBranchRef)
            val project = dataOnServer.children.single()
            val moduleData = project.children.single()
            val model = moduleData.children.single { it.role == "models" }
            assertEquals("aSolution.aModel", model.properties["name"])
        }
    }
}
