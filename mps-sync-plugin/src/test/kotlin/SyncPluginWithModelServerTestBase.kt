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

import com.intellij.util.io.exists
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.client2.runWriteOnBranch
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.data.asData
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.mps.sync.plugin.configuration.env.MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_BRANCH
import org.modelix.mps.sync.plugin.configuration.env.MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_ENABLED
import org.modelix.mps.sync.plugin.configuration.env.MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_REPOSITORY
import org.modelix.mps.sync.plugin.configuration.env.MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_URL
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.nio.file.Path

private val iNamedConcept = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept
private val moduleConcept = BuiltinLanguages.MPSRepositoryConcepts.Module
private val modelConcept = BuiltinLanguages.MPSRepositoryConcepts.Model
private val baseConcept = BuiltinLanguages.jetbrains_mps_lang_core.BaseConcept

open class SyncPluginWithModelServerTestBase : SyncPluginTestBase() {
    private var modelServer = GenericContainer(DockerImageName.parse("modelix/model-server:8.14.1"))
        .withCommand("-inmemory")
        .withExposedPorts(28101)
    lateinit var baseUrl: String
    lateinit var modelClient: ModelClientV2
    val repositoryId = RepositoryId("aRepository")
    val branchRef = repositoryId.getBranchReference("aBranch")

    public override fun setUp() {
        modelServer.start()
        baseUrl = "http://${modelServer.host}:${modelServer.firstMappedPort}/v2"
        modelClient = ModelClientV2.builder().url(baseUrl).build()
        runBlocking {
            modelClient.init()
            setUpModelServerData()
        }
        super.setUp()
        if (loggedErrorsDuringStartup.isNotEmpty()) {
            fail("Startup errors encountered: $loggedErrorsDuringStartup")
        }
    }

    private fun setUpModelServerData() {
        val testSpecificDataName = getTestSpecificDataName()
        val modelServerDataFile = File("testdata/$testSpecificDataName/modelServerData.json")
        if (!modelServerDataFile.exists()) {
            return
        }
        val modelServerData: ModelData = Json.decodeFromString(modelServerDataFile.readText())
        runBlocking {
            modelClient.initRepository(repositoryId)
            modelClient.runWriteOnBranch(branchRef) { branch ->
                modelServerData.load(branch, null, setOriginalIdProperty = false)
            }
        }
    }

    private fun getTestSpecificDataName(): String {
        // e.g.`initialModuleFromModelServer` becomes the test data for
        // `testUpdateFromModelServer_with_initialModuleFromModelServer`
        val testDataName = getTestName(false).split("_with_")[1]
        val testDataFolder = Path.of("testdata/$testDataName")
        if (!testDataFolder.exists()) {
            throw IllegalStateException("Test data $testDataFolder does not exist.")
        }
        // TODO Olekz return foler here
        return testDataName
    }

    override fun getByKeyCustomEnvValue(): Map<String, String> {
        return mutableMapOf(
            MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_ENABLED to "true",
            MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_URL to baseUrl,
            MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_REPOSITORY to repositoryId.id,
            MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_BRANCH to branchRef.branchName,
        )
    }

    override fun tearDown() {
        try {
            modelClient.close()
            modelServer.stop()
        } finally {
            super.tearDown()
        }
    }

    override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path {
        val projectDir = super.getProjectDirOrFile(isDirectoryBasedProject)
        val testSpecificDataName = getTestSpecificDataName()
        val mpsSourceDir = File("testdata/$testSpecificDataName/mpsData")
        if (mpsSourceDir.exists()) {
            mpsSourceDir.copyRecursively(projectDir.toFile(), overwrite = true)
        }
        this.projectDir = projectDir
        return projectDir
    }
}

fun SyncPluginWithModelServerTestBase.waitUntilModuleIsOnServer(vararg expectedModuleNames: String) {
    waitUntil("Branch is created") {
        modelClient.listRepositories().contains(repositoryId)
    }
    waitUntil("Repository is created") {
        modelClient.listBranches(repositoryId).contains(branchRef)
    }
    waitUntil("Modules ${listOf(*expectedModuleNames)} are on model server.") {
        val modulesOnServer = getModulesFromServer()
        expectedModuleNames.all { expectedModuleName ->
            modulesOnServer.any { moduleOnServer ->
                moduleOnServer.getPropertyValue(iNamedConcept.name) == expectedModuleName
            }
        }
    }
}

suspend fun SyncPluginWithModelServerTestBase.getModulesFromServer(): List<INode> {
    val version = modelClient.pull(branchRef, null) as CLVersion
    val branch = TreePointer(version.getTree(), modelClient.getIdGenerator())
    val modulesOnServer = branch.getRootNode()
        .allChildren
        .filter { it.concept == moduleConcept }
    return modulesOnServer
}

fun SyncPluginWithModelServerTestBase.waitUntilModuleIsInMps(vararg expectedModuleNames: String) {
    // TODO Olekzk use await
    waitUntil("Modules ${listOf(*expectedModuleNames)} are in MPS.") {
        val mpsModule = runReadMps { mpsProject.projectModules }
        expectedModuleNames.all { expectedModuleName ->
            mpsModule.any { mpsModule ->
                mpsModule.moduleName == expectedModuleName
            }
        }
    }
}

// TODO Olekz remove
@OptIn(UnstableModelixFeature::class)
fun SyncPluginWithModelServerTestBase.waitUntilSynced() {
    waitUntil(
        "tmp",
        timeoutMilliseconds = 600_000,
        step = 60_000,
    ) {
        normalizeNodeDataRoot(readDumpFromServer()) == normalizeNodeDataRoot(readDumpFromMPS())
    }
//
//    await()
//        .atMost(Duration.TEN_SECONDS)
//        .with()
//        .pollInterval(Duration.ONE_HUNDRED_MILLISECONDS)
//        .untilAsserted {
//            val serviceLocator = project.service<ServiceLocator>()
//            println(serviceLocator)
//            println(serviceLocator.syncQueue)
// //            println(serviceLocator.syncQueue)
//            assertEquals(normalizeNodeDataRoot(readDumpFromServer()), normalizeNodeDataRoot(readDumpFromMPS()))
//        }
}

suspend fun <R> SyncPluginWithModelServerTestBase.runWithNewConnection(body: suspend (IModelClientV2) -> R): R =
    runBlocking {
        val client = ModelClientV2.builder().url(baseUrl).build()
        client.init()
        body(client)
    }

fun SyncPluginWithModelServerTestBase.readDumpFromMPS(): NodeData = runReadMps {
    val mpsModules = mpsProject.projectModules
    val mpsModulesAsNodeData = mpsModules.map { mpsModule ->
        MPSModuleAsNode(mpsModule).asData()
            .copy(role = "modules")
    }
    NodeData(
        id = ITree.ROOT_ID.toString(),
        children = mpsModulesAsNodeData,
    )
}

fun SyncPluginWithModelServerTestBase.readDumpFromServer(): NodeData =
    runBlocking {
        val versionOnServer = modelClient.pull(branchRef, null)
        val branch = TreePointer(versionOnServer.getTree())
        branch.getRootNode().asData().copy(id = ITree.ROOT_ID.toString())
    }

fun SyncPluginWithModelServerTestBase.getMpsModel(modelName: String) =
    mpsProject.projectModules
        .flatMap { module -> module.models }
        .single { model -> model.name.value == modelName }

fun SyncPluginWithModelServerTestBase.getModel(modelName: String): INode = runBlocking {
    getModulesFromServer()
        .flatMap { module -> module.getChildren(moduleConcept.models) }
        .single { model -> model.getPropertyValue(iNamedConcept.name) == modelName }
}

fun IBranch.getModel(modelName: String): INode {
    val modules = getRootNode()
        .allChildren
        .filter { it.concept == moduleConcept }
    val models = modules.flatMap { module -> module.getChildren(moduleConcept.models) }
    val model = models.single { model -> model.getPropertyValue(iNamedConcept.name) == modelName }
    return model
}

private fun normalizeNodeDataRoot(originalRoot: NodeData): NodeData {
    val normalizedRoot = originalRoot.copy(
        id = ITree.ROOT_ID.toString(),
        children = originalRoot.children.map(::normalizeNodeDataModule),
    )
    return normalizedRoot
}

fun normalizeNodeDataModule(module: NodeData): NodeData {
    val normalizedProperties = module.properties
        // TODO Olekz test if this line is really needed
        .filterNot { (propertyUid, _) -> baseConcept.virtualPackage.getUID() == propertyUid }
        .toSortedMap()
    return module.copy(
        id = module.properties[moduleConcept.id.getUID()],
        properties = normalizedProperties,
        children = module.children.map { normalizeNodeDataModel(module, it) },
    )
}

fun normalizeNodeDataModel(module: NodeData, model: NodeData): NodeData {
    val normalizedProperties = model.properties
        // TODO Olekz should stereotype be synced
        .filterNot { (propertyUid, _) -> modelConcept.stereotype.getUID() == propertyUid }
        // TODO Olekz should virtualPackage be synced
        // TODO Olekz test if this line is really needed
        .filterNot { (propertyUid, _) -> baseConcept.virtualPackage.getUID() == propertyUid }
    return model.copy(
        id = model.properties[modelConcept.id.getUID()],
        properties = normalizedProperties,
        children = model.children.map { normalizeNodeDataRegularNode(module, model, it) },
    )
}

fun normalizeNodeDataRegularNode(module: NodeData, model: NodeData, node: NodeData): NodeData {
    val nodeId = requireNotNull(node.id)

    val normalizedId = when {
        nodeId.startsWith("pnode:") -> node.properties[NodeData.ID_PROPERTY_KEY]
        // e.g. mps:61f6ca84-d13b-409d-8cc9-522f39d2cbf5/r:75513349-da4d-48b1-a0fe-307649981c92(aSolution/aSolution.aModel)/221840348703725611
        nodeId.startsWith("mps:") -> nodeId.split("/").last()
        else -> throw IllegalArgumentException("Unexpected ID in node `$node`.")
    }
    val normalizedModuleProperties = node.properties
        .filterNot { (propertyUid, _) -> NodeData.ID_PROPERTY_KEY == propertyUid }

    return node.copy(
        id = normalizedId,
        properties = normalizedModuleProperties,
        children = node.children.map { normalizeNodeDataRegularNode(module, model, it) },
    )
}

internal fun <R> SyncPluginWithModelServerTestBase.writeModelServer(body: (IBranch) -> R) =
    runBlocking {
        modelClient.runWriteOnBranch(branchRef, body)
    }
