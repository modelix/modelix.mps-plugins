package org.modelix.model.mpsplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFileManager
import jetbrains.mps.extapi.model.SModelData
import jetbrains.mps.ide.MPSCoreComponents
import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.internal.collections.runtime.SetSequence
import jetbrains.mps.persistence.DefaultModelPersistence
import jetbrains.mps.persistence.DefaultModelRoot
import jetbrains.mps.persistence.LazyLoadFacility
import jetbrains.mps.persistence.MementoImpl
import jetbrains.mps.persistence.ModelCannotBeCreatedException
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Project
import jetbrains.mps.project.Solution
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.project.structure.modules.ModuleFacetDescriptor
import jetbrains.mps.project.structure.modules.SolutionDescriptor
import jetbrains.mps.project.structure.modules.SolutionKind
import jetbrains.mps.smodel.DefaultSModel
import jetbrains.mps.smodel.DefaultSModelDescriptor
import jetbrains.mps.smodel.GeneralModuleFactory
import jetbrains.mps.smodel.SModel
import jetbrains.mps.smodel.SModelHeader
import jetbrains.mps.smodel.SModelId
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.loading.ModelLoadResult
import jetbrains.mps.smodel.loading.ModelLoadingState
import jetbrains.mps.smodel.persistence.def.ModelPersistence
import jetbrains.mps.smodel.persistence.def.ModelReadException
import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.IFileSystem
import jetbrains.mps.vfs.VFSManager
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.persistence.DataSource
import org.jetbrains.mps.openapi.persistence.Memento
import org.jetbrains.mps.openapi.persistence.ModelFactory
import org.jetbrains.mps.openapi.persistence.ModelLoadingOption
import org.jetbrains.mps.openapi.persistence.ModelRoot
import org.jetbrains.mps.openapi.persistence.ModelSaveException
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.jetbrains.mps.openapi.persistence.StreamDataSource
import org.jetbrains.mps.openapi.persistence.UnsupportedDataSourceException
import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.ITransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.IdGeneratorDummy
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PNodeAdapter.Companion.wrap
import org.modelix.model.area.PArea
import org.modelix.model.client.RestWebModelClient
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.CLVersion.Companion.loadFromHash
import org.modelix.model.lazy.PrefetchCache.Companion.with
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.metameta.MetaModelBranch
import org.modelix.model.mpsadapters.mps.SConceptAdapter
import org.modelix.model.mpsplugin.ModelCloudExporter.PersistenceFacility
import java.io.File
import java.io.IOException
import java.util.LinkedList
import java.util.UUID

/*Generated by MPS */
class ModelCloudExporter {
    private var branchName: String = DEFAULT_BRANCH_NAME
    private var repositoryInModelServer: CloudRepository?
    private var inCheckoutMode: Boolean = false
    fun setCheckoutMode(): ModelCloudExporter {
        inCheckoutMode = true
        return this
    }

    constructor(url: String?, repositoryId: String?, branchName: String?) {
        var url: String? = url
        var repositoryId: String? = repositoryId
        if ((url == null || url.length == 0)) {
            url = DEFAULT_URL
        }
        if (!(url.endsWith("/"))) {
            url += "/"
        }
        if ((repositoryId == null || repositoryId.length == 0)) {
            repositoryId = DEFAULT_TREE_ID
        }
        val modelServer: ModelServerConnection? = ModelServerConnections.instance.getModelServer(url)
        if (modelServer == null) {
            throw IllegalStateException("No ModelServer connection found for url " + url)
        }
        repositoryInModelServer = CloudRepository(modelServer, RepositoryId(repositoryId))
        if ((branchName != null && branchName.length > 0)) {
            this.branchName = branchName
        } else {
            this.branchName = DEFAULT_BRANCH_NAME
        }
    }

    @JvmOverloads
    constructor(
        treeInRepository: CloudRepository?,
        branchName: String? = treeInRepository?.activeBranch?.branchName
    ) {
        repositoryInModelServer = treeInRepository
        if (branchName == null) {
            this.branchName = DEFAULT_BRANCH_NAME
        } else {
            this.branchName = branchName
        }
    }

    fun export(exportPath: String?, mpsProject: Project?): List<Solution> {
        return export(exportPath, null, mpsProject)
    }

    /**
     *
     *
     * @param exportPath
     * @param selectedMduleIds null indicates all modules
     * @param mpsProject
     * @return
     */
    fun export(exportPath: String?, selectedMduleIds: Set<Long>?, mpsProject: Project?): List<Solution> {
        val coreComponents: MPSCoreComponents = ApplicationManager.getApplication().getComponent(
            MPSCoreComponents::class.java
        )
        val vfsManager: VFSManager? = coreComponents.getPlatform().findComponent(VFSManager::class.java)
        val fileSystem: IFileSystem = vfsManager!!.getFileSystem(VFSManager.FILE_FS)
        val outputFolder: IFile = fileSystem.getFile(File(exportPath))
        return export(outputFolder, selectedMduleIds, mpsProject)
    }

    fun export(outputFolder: IFile, mpsProject: Project?): List<Solution> {
        return export(outputFolder, null, mpsProject)
    }

    /**
     * This method is expected to be called when a user is present to see the notifications.
     *
     * @param outputFolder
     * @param selectedModuleIds null indicates all modules
     * @param mpsProject
     * @return
     */
    fun export(outputFolder: IFile, selectedModuleIds: Set<Long>?, mpsProject: Project?): List<Solution> {
        println("exporting to " + outputFolder.getPath())
        println("the output folder exists? " + outputFolder.exists())
        if (!(inCheckoutMode)) {
            outputFolder.deleteIfExists()
        }
        val url: String? = repositoryInModelServer?.modelServer?.baseUrl
        val client: RestWebModelClient = RestWebModelClient((url)!!)
        val repositoryId: RepositoryId? = repositoryInModelServer?.repositoryId
        val branchKey: String = repositoryId!!.getBranchKey(branchName)
        var versionHash: String? = null
        try {
            versionHash = client.get(branchKey)
        } catch (e: Exception) {
            throw RuntimeException("Issue retrieving key " + branchKey + " with base URL " + client.baseUrl, e)
        }
        if ((versionHash == null || versionHash.length == 0)) {
            throw RuntimeException("No version found at " + url + "get/" + branchKey + ". Base URL " + client.baseUrl)
        }
        val version: CLVersion? = loadFromHash(versionHash, client.storeCache)
        if (version == null) {
            throw RuntimeException("Branch " + branchKey + " references non-existent version " + versionHash)
        }
        return repositoryInModelServer!!.computeRead<List<Solution>>({
            val tree: CLTree = version.getTree()
            val branch: IBranch = MetaModelBranch(PBranch(tree, IdGeneratorDummy()))
            PArea(branch).executeRead({
                with<List<Solution>>(tree, {
                    val t: ITransaction = branch.transaction
                    var moduleIds: Iterable<Long>? = t.getChildren(ITree.ROOT_ID, LINKS.`modules$jBPn`.getName())
                    if (moduleIds != null && selectedModuleIds != null) {
                        moduleIds = Sequence.fromIterable(moduleIds).intersect(SetSequence.fromSet(selectedModuleIds))
                    }

                    // prefetch module contents
                    tree.getDescendants((moduleIds)!!, true)
                    val modules: List<INode> =
                        Sequence.fromIterable<Long>(moduleIds).select<INode>(object : ISelector<Long?, INode>() {
                            public override fun select(it: Long?): INode {
                                val n: INode = PNodeAdapter((it)!!, branch)
                                return n
                            }
                        }).toListSequence()
                    createModules(modules, outputFolder, mpsProject)
                })
            })
        })
    }

    /**
     * This method is expected to be called when a user is present to see the notifications.
     */
    private fun createModules(modules: List<INode>, outputFolder: IFile, mpsProject: Project?): List<Solution> {
        val solutions: List<Solution> = ListSequence.fromList(LinkedList())
        for (module: INode in modules) {
            val s: Solution? = createModule(module, outputFolder, mpsProject)
            if (s != null) {
                ListSequence.fromList(solutions).addElement(s)
            }
        }
        return solutions
    }

    /**
     * We experienced issues with physical and virtual files being out of sync.
     * This method ensure that files are deleted, recursively both on the virtual filesystem and the physical filesystem
     */
    private fun ensureDeletion(virtualFile: IFile) {
        if (virtualFile.isDirectory()) {
            for (child: IFile in ListSequence.fromList(virtualFile.getChildren())) {
                ensureDeletion(child)
            }
        } else {
            if (virtualFile.exists()) {
                virtualFile.delete()
            }
            val physicalFile: File = File(virtualFile.getPath())
            physicalFile.delete()
        }
    }

    private fun ensureDirDeletionAndRecreation(virtualDir: IFile) {
        ensureDeletion(virtualDir)
        virtualDir.mkdirs()
    }

    /**
     * This method is expected to be called when a user is present to see the notification.
     */
    private fun createModule(module: INode, outputFolder: IFile, mpsProject: Project?): Solution? {
        val name: String? = module.getPropertyValue("name")
        val moduleIdAsString: String? = module.getPropertyValue("id")
        if (moduleIdAsString == null) {
            ModelixNotifications.notifyError(
                "Module without ID",
                "Module " + name + " has been stored without an ID. Please set the ID and check it out again"
            )
            return null
        }
        val coreComponents: MPSCoreComponents = ApplicationManager.getApplication().getComponent(
            MPSCoreComponents::class.java
        )
        val vfsManager: VFSManager? = coreComponents.getPlatform().findComponent(VFSManager::class.java)
        val fileSystem: IFileSystem = vfsManager!!.getFileSystem(VFSManager.FILE_FS)
        if (!(inCheckoutMode)) {
            outputFolder.deleteIfExists()
        }
        val solutionFile: IFile = outputFolder.findChild((name)!!).findChild("solution" + MPSExtentions.DOT_SOLUTION)
        val solutionDir: IFile = outputFolder.findChild((name)!!)
        if (inCheckoutMode) {
            ApplicationManager.getApplication().invokeAndWait(object : Runnable {
                public override fun run() {
                    VirtualFileManager.getInstance().syncRefresh()
                    val modelsDirVirtual: IFile = solutionDir.findChild("models")
                    ensureDirDeletionAndRecreation(modelsDirVirtual)
                }
            })
        }
        val descriptor: SolutionDescriptor = SolutionDescriptor()
        descriptor.setNamespace(name)
        val solutionId: ModuleId = ModuleId.regular(UUID.fromString(moduleIdAsString))
        descriptor.setId(solutionId)
        descriptor.getModelRootDescriptors().add(
            DefaultModelRoot.createDescriptor(
                (solutionFile.getParent())!!, solutionFile.getParent()!!.findChild(Solution.SOLUTION_MODELS)
            )
        )
        descriptor.setKind(SolutionKind.PLUGIN_OTHER)
        for (facet: INode in Sequence.fromIterable<INode>(module.getChildren("facets"))) {
            if (facet.concept!!.isSubConceptOf(SConceptAdapter.Companion.wrap(CONCEPTS.`JavaModuleFacet$5E`))) {
                val javaFacetMemento: Memento = MementoImpl()
                val javaFacetClassesMemento: Memento = javaFacetMemento.createChild("classes")
                javaFacetClassesMemento.put("generated", facet.getPropertyValue("generated"))
                javaFacetClassesMemento.put(
                    "path", facet.getPropertyValue("path")!!
                        .replace("\\$\\{module\\}".toRegex(), solutionFile.getParent()!!.toRealPath())
                )
                val javaFacetDescriptor: ModuleFacetDescriptor =
                    ModuleFacetDescriptor(JavaModuleFacet.FACET_TYPE, javaFacetMemento)
                descriptor.getModuleFacetDescriptors().add(javaFacetDescriptor)
            }
        }
        val solution: Solution = GeneralModuleFactory().instantiate(descriptor, solutionFile) as Solution
        if (mpsProject != null) {
            mpsProject.addModule(solution)
            if (solution.getRepository() == null) {
                solution.attach(mpsProject.getRepository())
            }
        }
        if (mpsProject != null && solution.getRepository() == null) {
            throw IllegalStateException("The solution should be in a repo, so also the model will be in a repo and syncReference will not crash")
        }
        for (model: INode in module.getChildren("models")) {
            createModel(solution, model)
        }
        solution.save()
        return solution
    }

    /**
     * We had to copy it from https://github.com/JetBrains/MPS/blob/14b86a2f987cdd3fbcc72b9262e8b388f7a5fae3/core/persistence/source/jetbrains/mps/persistence/DefaultModelPersistence.java#L115
     */
    private class PersistenceFacility  /*package*/
    internal constructor(modelFactory: DefaultModelPersistence?, dataSource: StreamDataSource?) :
        LazyLoadFacility((modelFactory)!!, (dataSource)!!, true) {
        private val source0: StreamDataSource
            private get() {
                return super.getSource() as StreamDataSource
            }

        @Throws(ModelReadException::class)
        public override fun readHeader(): SModelHeader {
            return ModelPersistence.loadDescriptor(source0)
        }

        @Throws(ModelReadException::class)
        public override fun readModel(header: SModelHeader, state: ModelLoadingState): ModelLoadResult {
            return ModelPersistence.readModel(header, source0, state)
        }

        public override fun doesSaveUpgradePersistence(header: SModelHeader): Boolean {
            // not sure !=-1 is really needed, just left to be ensured about compatibility
            return header.getPersistenceVersion() != ModelPersistence.LAST_VERSION && header.getPersistenceVersion() != -1
        }

        @Throws(IOException::class)
        public override fun saveModel(header: SModelHeader, modelData: SModelData) {
            val res: AsyncPromise<Boolean> = AsyncPromise()
            ThreadUtils.runInUIThreadNoWait(object : Runnable {
                public override fun run() {
                    try {
                        ModelPersistence.saveModel((modelData as SModel?)!!, source0, header.getPersistenceVersion())
                        res.setResult(true)
                    } catch (e: ModelSaveException) {
                        e.printStackTrace()
                        res.setResult(false)
                    }
                }
            })
            if (!(res.get())!!) {
                throw RuntimeException("Unable to save model")
            }
        }
    }

    private fun createModel(module: AbstractModule, model: INode) {
        val modelRootsIt: Iterator<ModelRoot> = module.getModelRoots().iterator()
        if (!(modelRootsIt.hasNext())) {
            throw IllegalStateException("Module has not default model root: " + module + " (" + module.getModuleName() + ")")
        }
        val defaultModelRoot: DefaultModelRoot = (modelRootsIt.next() as DefaultModelRoot)
        val sModelName: SModelName = SModelName((model.getPropertyValue("name"))!!)
        val imposedModelID: SModelId = SModelId.fromString(model.getPropertyValue("id"))
        val modelFactory: ModelFactory =
            object : ModelPersistenceWithFixedId(module.getModuleReference(), imposedModelID) {
                @Throws(UnsupportedDataSourceException::class)
                public override fun create(
                    dataSource: DataSource,
                    modelName: SModelName,
                    vararg options: ModelLoadingOption
                ): org.jetbrains.mps.openapi.model.SModel {
                    // COPIED FROM https://github.com/JetBrains/MPS/blob/14b86a2f987cdd3fbcc72b9262e8b388f7a5fae3/core/persistence/source/jetbrains/mps/persistence/DefaultModelPersistence.java#L115
                    if (!((supports(dataSource)))) {
                        throw UnsupportedDataSourceException(dataSource)
                    }
                    val header: SModelHeader = SModelHeader.create(ModelPersistence.LAST_VERSION)
                    val modelReference: SModelReference =
                        PersistenceFacade.getInstance().createModelReference(null, imposedModelID, modelName.getValue())
                    header.setModelReference(modelReference)
                    val rv: DefaultSModelDescriptor =
                        DefaultSModelDescriptor(PersistenceFacility(this, dataSource as StreamDataSource?), header)
                    // Hack to ensure newly created model is indeed empty. Otherwise, with StreamDataSource pointing to existing model stream, an attempt to
                    // do anything with the model triggers loading and the model get all the data. Two approaches deemed reasonable to tackle the issue:
                    // (a) enforce clear empty model (why would anyone call #create() then)
                    // (b) fail with error (too brutal?)
                    // Another alternative considered is to tolerate any DataSource in DefaultSModelDescriptor (or its persistence counterpart), so that
                    // one can create an empty model with NullDataSource, and later save with a proper DataSource (which yields more job to client and makes him
                    // question why SModel.save() is there). This task is reasonable regardless of final approach taken, but would take more effort, hence the hack.
                    if (dataSource.getTimestamp() != -1L) {
                        // chances are there's something in the stream already
                        rv.replace(DefaultSModel(modelReference, header))
                        // model state is FULLY_LOADED, DataSource won't get read
                    }
                    return rv
                }
            }
        // We create models asynchronously, similarly to what is done in mpsutil.smodule
        // this helps avoiding issues with VFS and physical FS being out of sync
        VirtualFileManager.getInstance().syncRefresh()
        val res: AsyncPromise<EditableSModel> = AsyncPromise()
        ThreadUtils.runInUIThreadNoWait(object : Runnable {
            public override fun run() {
                try {
                    println("creating model " + sModelName)
                    val smodel: EditableSModel =
                        defaultModelRoot.createModel(sModelName, null, null, modelFactory) as EditableSModel
                    println("  model " + sModelName + " created")
                    res.setResult(smodel)
                } catch (e: ModelCannotBeCreatedException) {
                    res.setResult(null)
                    throw RuntimeException(e)
                }
            }
        })
        val smodel: EditableSModel? = res.get()
        if (smodel != null) {
            ModelSynchronizer((model as PNodeAdapter).nodeId, smodel, repositoryInModelServer!!).syncModelToMPS(
                model.branch.transaction.tree, true
            )
            module.getRepository()!!.getModelAccess().runWriteAction(object : Runnable {
                public override fun run() {
                    smodel.save()
                    println("  model " + sModelName + " saved")
                }
            })
        }
    }

    private object LINKS {
        /*package*/
        val `modules$jBPn`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x69652614fd1c516L,
            0x69652614fd1c517L,
            "modules"
        )
    }

    private object CONCEPTS {
        /*package*/
        val `JavaModuleFacet$5E`: SConcept = MetaAdapterFactory.getConcept(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x1e9fde9535299166L,
            "org.modelix.model.repositoryconcepts.structure.JavaModuleFacet"
        )
    }

    companion object {
        private val DEFAULT_BRANCH_NAME: String = "master"
        private val DEFAULT_URL: String = "http://localhost:28101/"
        private val DEFAULT_TREE_ID: String = "default"
    }
}