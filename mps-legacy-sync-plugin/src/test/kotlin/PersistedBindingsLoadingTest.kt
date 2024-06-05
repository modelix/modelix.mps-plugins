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

import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom
import jetbrains.mps.smodel.SModelId
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.mpsplugin.ModelServerConnections
import org.modelix.model.mpsplugin.SModuleUtils
import java.util.UUID

class PersistedBindingsLoadingTest : SyncPluginTestBase("projectWithPersistedBindings") {

    fun `test persisted bindings are loaded initially`() = runTestWithSyncService {
        // Arrange
        delayUntil {
            val modelServer = ModelServerConnections.instance.modelServers
                .firstOrNull()
            if (modelServer == null) {
                return@delayUntil false
            }
            return@delayUntil modelServer.getRootBindings().isNotEmpty()
        }

        // Act
        val existingSolution = mpsProject.projectModules.single()
        writeAction {
            SModuleUtils.createModel(
                existingSolution,
                "my.wonderful.brandnew.modelInExistingModule",
                SModelId.regular(UUID.fromString("1c22f2f9-f1f3-45f8-8f4b-69b248928af5")),
            )
        }

        // Assert
        delayUntil {
            val dataOnServer = readDumpFromServer(defaultBranchRef)
            val moduleData = dataOnServer.children.single()
            val models = moduleData.children.filter { it.role == "models" }
            models.size == 2
        }

        // XXX Project bindings do not close opened model server connections,
        // because in the meantime they might be used by other project bindings.
        // This should be solved by the new plugin.
        val modelServers = ModelServerConnections.instance.modelServers.toList()
        modelServers.forEach {
            ModelServerConnections.instance.removeModelServer(it)
        }
    }

    override suspend fun postModelServerSetup() {
        val moduleConcept = BuiltinLanguages.MPSRepositoryConcepts.Module
        httpClient.post {
            url {
                takeFrom(baseUrl)
                appendPathSegments("repositories", "${defaultBranchRef.repositoryId}", "init")
                parameter("useRoleIds", "false")
                // TODO Olekz check and comment, why this is needed
                parameter("legacyGlobalStorage", "true")
            }
        }
        ModelClientV2.builder().url(baseUrl).client(httpClient).build().use { client ->
            client.init()
            client.runWrite(defaultBranchRef) { root ->
                val module = root.addNewChild("modules", -1, moduleConcept)
                module.setPropertyValue("name", "NewSolution")
            }
        }
    }
}
