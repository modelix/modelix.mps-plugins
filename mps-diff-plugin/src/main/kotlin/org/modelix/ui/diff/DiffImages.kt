package org.modelix.ui.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.chains.DiffRequestProducerException
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.FutureResult
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitCommit
import git4idea.changes.GitChangeUtils
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepositoryImpl
import jetbrains.mps.baseLanguage.closures.runtime.Wrappers._T
import jetbrains.mps.baseLanguage.closures.runtime._FunctionTypes._return_P0_E0
import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.internal.collections.runtime.CollectionSequence
import jetbrains.mps.internal.collections.runtime.ILeftCombinator
import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.IVisitor
import jetbrains.mps.internal.collections.runtime.IWhereFilter
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.internal.collections.runtime.SetSequence
import jetbrains.mps.nodeEditor.EditorComponent
import jetbrains.mps.nodeEditor.NodeHighlightManager
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.smodel.MPSModuleRepository
import jetbrains.mps.vcs.diff.ui.RootDifferencePaneBase
import jetbrains.mps.vcs.diff.ui.common.DiffModelTree
import jetbrains.mps.vcs.diff.ui.common.DiffModelTree.RootTreeNode
import jetbrains.mps.vcs.platform.integration.ModelDiffViewer
import jetbrains.mps.vfs.IFile
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.jetbrains.mps.openapi.persistence.datasource.FileExtensionDataSourceType
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.Future
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.TreePath
import kotlin.math.max
import kotlin.math.min

class DiffImages(
    private val project: Project = (ProjectManager.getInstance().openProjects + ProjectManager.getInstance().defaultProject).first(),
) {
    init {
        PropertiesComponent.getInstance()
            .setValue(RootDifferencePaneBase::class.java.name + "ShowInspector", false, true)
    }

    val repoRoots: List<VirtualFile>?
        get() {
            val repoRoots = ArrayList<VirtualFile>()

            val gitRepoDirPath = getPropertyOrEnv("GIT_REPO_DIR")
            if (gitRepoDirPath != null) {
                val repoRoot = LocalFileSystem.getInstance().findFileByIoFile(File(gitRepoDirPath))
                if (repoRoot != null) {
                    ensureRepoLoaded(project, repoRoot)
                    repoRoots += repoRoot
                }
            }

            if (repoRoots.isEmpty()) {
                for (repo: Repository in CollectionSequence.fromCollection(
                    VcsRepositoryManager.getInstance(
                        project,
                    ).repositories,
                )) {
                    repoRoots += repo.root
                }
            }

            repoRoots.addAll(additionalGitRepos.mapNotNull { LocalFileSystem.getInstance().findFileByIoFile(it)?.also { ensureRepoLoaded(project, it) } })

            if (repoRoots.isEmpty()) {
                val moduleRepo = MPSModuleRepository.getInstance()
                lateinit var moduleFiles: List<IFile>
                moduleRepo.modelAccess.runReadAction {
                    moduleFiles = moduleRepo.modules.filterIsInstance<AbstractModule>().mapNotNull { it.moduleSourceDir }
                }
                val gitRootCandidates = ancestorFiles(moduleFiles)
                repoRoots += gitRootCandidates.filter { it.findChild(".git").exists() }.mapNotNull { toVirtualFile(it) }
                repoRoots.forEach { ensureRepoLoaded(project, it) }
            }

            if (repoRoots.isEmpty()) {
                throw RuntimeException("No repository root found")
            }
            return repoRoots
        }

    private fun ancestorFiles(files: List<IFile>): List<IFile> {
        if (files.isEmpty()) {
            return emptyList()
        }
        val parentFiles = files.mapNotNull { it.parent } - files.toSet()
        return parentFiles + ancestorFiles(parentFiles)
    }

    private fun toVirtualFile(file: IFile): VirtualFile? {
        return LocalFileSystem.getInstance().findFileByPath(file.path)
    }

    fun diff(
        repoRoot: VirtualFile = ListSequence.fromList(
            repoRoots,
        ).first(),
    ): List<() -> List<DiffImage>> {
        if (LOG.isInfoEnabled) {
            LOG.info("Repo root: $repoRoot")
        }
        try {
            val history = GitHistoryUtils.history(project, repoRoot, "-n1")
            val commit = history[0]
            return diffCommit(commit, repoRoot)
        } catch (ex: Exception) {
            LOG.error(ex) { "" }
            throw RuntimeException(ex)
        }
    }

    fun diffRevisions(
        leftRevision: String?,
        rightRevision: String?,
        repoRoots: List<VirtualFile>? = this.repoRoots,
    ): List<() -> List<DiffImage>> {
        var repoRoot: VirtualFile? = null
        if (ListSequence.fromList<VirtualFile>(repoRoots).count() > 1) {
            // find the root that contains the revisions
            for (rr: VirtualFile? in ListSequence.fromList<VirtualFile>(repoRoots)) {
                try {
                    GitChangeUtils.resolveReference(project, (rr)!!, (leftRevision)!!)
                    GitChangeUtils.resolveReference(project, (rr)!!, (rightRevision)!!)
                    repoRoot = rr
                } catch (ex: VcsException) {
                }
            }
        } else {
            repoRoot = ListSequence.fromList(repoRoots).first()
        }
        try {
            val changes = GitChangeUtils.getDiff(
                project,
                (repoRoot)!!,
                leftRevision,
                rightRevision,
                ListSequence.fromListAndArray(
                    ArrayList(),
                    VcsUtil.getFilePath(
                        (repoRoot),
                    ),
                ),
            )
            return diffChanges(changes, repoRoot)
        } catch (e: VcsException) {
            throw RuntimeException(e)
        }
    }

    @Throws(DiffRequestProducerException::class)
    fun diffCommit(commit: GitCommit, repoRoot: VirtualFile?): List<() -> List<DiffImage>> {
        val changes: Iterable<Change> = commit.changes
        return diffChanges(changes, repoRoot)
    }

    fun diffChanges(changes: Iterable<Change>, repoRoot: VirtualFile?): List<() -> List<DiffImage>> {
        return changes.filter { isModel(it) }.map { diffChange(it, repoRoot) }
    }

    /**
     * Call this method outside EDT and then execute the returned function on EDT
     */
    fun diffChange(change: Change, repoRoot: VirtualFile?): () -> List<DiffImage> {
        val context: DiffContext = object : DiffContext() {
            override fun getProject(): Project? {
                return this@DiffImages.project
            }

            override fun isWindowFocused(): Boolean {
                return false
            }

            override fun isFocusedInWindow(): Boolean {
                return false
            }

            override fun requestFocusInWindow() {
            }
        }
        val changeDiffRequestProducer = ChangeDiffRequestProducer.create(project, change)
        val diffRequest = _T<DiffRequest>()
        try {
            diffRequest.value = changeDiffRequestProducer!!.process(context, EmptyProgressIndicator())
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
        if (diffRequest.value is ErrorDiffRequest) {
            LOG.error((diffRequest.value as ErrorDiffRequest).exception) { "Diff failed" }
            throw RuntimeException((diffRequest.value as ErrorDiffRequest).exception)
        }
        return { renderImages(repoRoot, change, diffRequest.value, context) }
    }

    fun renderImages(
        repoRoot: VirtualFile?,
        change: Change,
        diffRequest: DiffRequest?,
        context: DiffContext?,
    ): List<DiffImage> {
        ThreadUtils.assertEDT()
        val images: List<DiffImage> = ListSequence.fromList(ArrayList())
        val modelDiffViewer = ModelDiffViewer((context)!!, (diffRequest as ContentDiffRequest?)!!)
        try {
            val viewer = modelDiffViewer.component
            val frame = FrameWrapper(project, null, false, "Modelix Diff Viewer", viewer)
            try {
                frame.show()
            } catch (ex: Exception) {
                LOG.error(ex) { "Cannot open frame in headless mode." }
            }
            val diffTree = collectComponents(viewer).filterIsInstance<DiffModelTree>().firstOrNull()
            if (diffTree != null) {
                val rows: List<TreePath> = getRows(diffTree).filter { (it.lastPathComponent as? RootTreeNode) != null }
                for (row: TreePath in Sequence.fromIterable(rows)) {
                    val treeNode: RootTreeNode =
                        ((as_o3wada_a0a0a1a4a3a53(row.lastPathComponent, RootTreeNode::class.java))!!)
                    diffTree.selectionPath = row
                    val editorComponents: Iterable<EditorComponent> =
                        ListSequence.fromList(collectComponents(viewer)).ofType(
                            EditorComponent::class.java,
                        )
                    Sequence.fromIterable(editorComponents).visitAll(object : IVisitor<EditorComponent>() {
                        override fun visit(editor: EditorComponent) {
                            editor.editorContext.repository.modelAccess.runReadAction {
                                ReflectionUtil_copy.callVoidMethod(
                                    NodeHighlightManager::class.java,
                                    editor.highlightManager,
                                    "refreshMessagesCache",
                                    arrayOf(),
                                    arrayOf(),
                                )
                            }
                        }
                    })

                    val componentToPaint = commonAncestor(
                        Sequence.fromIterable(editorComponents).ofType(
                            Component::class.java,
                        ),
                    )
                    layoutDiffView(componentToPaint)
                    val img = paintComponent(componentToPaint)
                    ListSequence.fromList(images).addElement(
                        DiffImage(
                            img,
                            Dimension(componentToPaint.width, componentToPaint.height),
                            relativize(
                                getAffectedFile(change),
                                repoRoot,
                            ),
                            treeNode.rootId,
                            treeNode.presentation,
                        ),
                    )
                }
            }
        } finally {
            modelDiffViewer.dispose()
        }
        return images
    }

    protected fun getRows(tree: JTree): Iterable<TreePath> {
        val rows: List<TreePath> = ListSequence.fromList(ArrayList())
        for (i in 0 until tree.rowCount) {
            ListSequence.fromList(rows).addElement(tree.getPathForRow(i))
        }

        return rows
    }

    fun relativize(path: FilePath, repoRoot: VirtualFile?): String {
        val file = path.path
        val folder = repoRoot!!.path
        if (file.startsWith(folder)) {
            var relative = file.substring(folder.length)
            if (relative.startsWith("/")) {
                relative = relative.substring(1)
            }
            return relative
        }
        return file
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger { }
        val additionalGitRepos: MutableSet<File> = LinkedHashSet()

        fun ensureRepoLoaded(project: Project, repoRoot: File) {
            ensureRepoLoaded(project, checkNotNull(LocalFileSystem.getInstance().findFileByIoFile(repoRoot)) { "File not found: $repoRoot" })
        }

        fun ensureRepoLoaded(project: Project, repoRoot: VirtualFile) {
            val vcsManager = VcsRepositoryManager.getInstance(project)
            if (vcsManager.getRepositoryForRoot(repoRoot) == null) {
                vcsManager.addExternalRepository(
                    repoRoot,
                    GitRepositoryImpl.createInstance(repoRoot, project, vcsManager, false),
                )
            }
        }

        private fun getPropertyOrEnv(name: String): String? {
            var value = System.getProperty(name)
            if (value == null || value.length == 0) {
                value = System.getenv(name)
            }
            return value
        }

        fun <T> onThreadPool(c: _return_P0_E0<out T>): Future<T> {
            val result = FutureResult<T>()
            ApplicationManager.getApplication().executeOnPooledThread(object : Runnable {
                override fun run() {
                    try {
                        result.set(c.invoke())
                    } catch (ex: Throwable) {
                        result.setException(ex)
                    }
                }
            })
            return result
        }

        fun <T> onEDT(c: _return_P0_E0<out T>): Future<T> {
            val result = FutureResult<T>()
            ThreadUtils.runInUIThreadNoWait(object : Runnable {
                override fun run() {
                    try {
                        result.set(c.invoke())
                    } catch (ex: Throwable) {
                        result.setException(ex)
                    }
                }
            })
            return result
        }

        private fun getAffectedFile(change: Change): FilePath {
            var rev = change.afterRevision
            if (rev == null) {
                rev = change.beforeRevision
            }
            return rev!!.file
        }

        private fun isModel(change: Change): Boolean {
            val ext = getAffectedFile(change).fileType.defaultExtension
            return PersistenceFacade.getInstance().getModelFactory(FileExtensionDataSourceType.of(ext)) != null
        }

        private fun collectComponents(comp: Component): List<Component> {
            val acc: List<Component> = ArrayList()
            collectComponents(comp, acc)
            return acc
        }

        private fun collectComponents(comp: Component?, acc: List<Component>) {
            if (comp == null) {
                return
            }
            ListSequence.fromList(acc).addElement(comp)
            if (comp is Container) {
                for (child: Component in comp.components) {
                    collectComponents(child, acc)
                }
            }
        }

        private fun paintComponent(component: Component): BufferedImage {
            val img = UIUtil.createImage(component, component.width, component.height, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            component.paint(g)
            return img
        }

        private fun layoutDiffView(viewer: Component) {
            viewer.preferredSize = null
            val components = collectComponents(viewer)
            ListSequence.fromList(components).visitAll(object : IVisitor<Component>() {
                override fun visit(it: Component) {
                    it.preferredSize = null
                }
            })
            ListSequence.fromList(components).ofType(JTree::class.java).visitAll(object : IVisitor<JTree>() {
                override fun visit(it: JTree) {
                    it.visibleRowCount = it.rowCount
                }
            })
            viewer.size = viewer.preferredSize
            viewer.setSize(viewer.width + 10, viewer.height + 10)
            layoutAll(viewer)
            for (timeout in 5 downTo 1) {
                for (timeout2 in 20 downTo 1) {
                    var anySplitterChanged = false
                    for (splitter: Splitter in ListSequence.fromList<Component>(components).ofType<Splitter>(
                        Splitter::class.java,
                    )) {
                        if (splitter.firstComponent == null || splitter.secondComponent == null) {
                            continue
                        }
                        var additional1: Float
                        var additional2: Float
                        var size1: Float
                        var size2: Float
                        if (splitter.isVertical) {
                            additional1 = calcAdditionalRequiredSize(splitter.firstComponent).height.toFloat()
                            additional2 = calcAdditionalRequiredSize(splitter.secondComponent).height.toFloat()
                            size1 = splitter.firstComponent.height.toFloat()
                            size2 = splitter.secondComponent.height.toFloat()
                        } else {
                            additional1 = calcAdditionalRequiredSize(splitter.firstComponent).width.toFloat()
                            additional2 = calcAdditionalRequiredSize(splitter.secondComponent).width.toFloat()
                            size1 = splitter.firstComponent.width.toFloat()
                            size2 = splitter.secondComponent.width.toFloat()
                        }
                        var newProportion = (size1 + additional1) / (size1 + size2 + additional1 + additional2)
                        newProportion = min(1.0, newProportion.toDouble()).toFloat()
                        newProportion = max(0.0, newProportion.toDouble()).toFloat()
                        if (newProportion != splitter.proportion) {
                            anySplitterChanged = true
                            splitter.proportion = newProportion
                            layoutAll(viewer)
                        }
                    }
                    if (!(anySplitterChanged)) {
                        break
                    }
                }
                val additionalRequiredSize = calcAdditionalRequiredSize(viewer)
                val size = viewer.size
                viewer.size =
                    Dimension(size.width + additionalRequiredSize.width, size.height + additionalRequiredSize.height)
                layoutAll(viewer)
                if ((additionalRequiredSize == Dimension(0, 0))) {
                    break
                }
            }
        }

        private fun layoutAll(comp: Component) {
            comp.doLayout()
            if (comp is Container) {
                for (child: Component in comp.components) {
                    layoutAll(child)
                }
            }
        }

        private fun calcAdditionalRequiredSize(component: Component): Dimension {
            return ListSequence.fromList(collectComponents(component)).where(object : IWhereFilter<Component?>() {
                override fun accept(it: Component?): Boolean {
                    return it !is JScrollPane
                }
            }).select(object : ISelector<Component, Dimension>() {
                override fun select(c: Component): Dimension {
                    val preferredSize = c.preferredSize
                    val size = c.size
                    return Dimension(
                        max(0.0, (preferredSize.width - size.width).toDouble()).toInt(),
                        max(0.0, (preferredSize.height - size.height).toDouble())
                            .toInt(),
                    )
                }
            }).foldLeft(
                Dimension(0, 0),
                object : ILeftCombinator<Dimension, Dimension>() {
                    override fun combine(s: Dimension, it: Dimension): Dimension {
                        return Dimension(
                            max(s.width.toDouble(), it.width.toDouble()).toInt(),
                            max(s.height.toDouble(), it.height.toDouble())
                                .toInt(),
                        )
                    }
                },
            )
        }

        fun commonAncestor(c1: Component?, c2: Component?): Component? {
            val ancestors: Set<Component> = SetSequence.fromSet(HashSet())
            run {
                var ancestor: Component? = c1
                while (ancestor != null) {
                    SetSequence.fromSet(ancestors).addElement(ancestor)
                    ancestor = ancestor!!.getParent()
                }
            }

            var ancestor = c2
            while (ancestor != null) {
                if (SetSequence.fromSet(ancestors).contains(ancestor)) {
                    return ancestor
                }
                ancestor = ancestor!!.parent
            }

            return null
        }

        private fun commonAncestor(components: Iterable<Component>): Component {
            return Sequence.fromIterable(components).reduceLeft(object : ILeftCombinator<Component?, Component?>() {
                override fun combine(a: Component?, b: Component?): Component? {
                    return commonAncestor(a, b)
                }
            })
        }

        private fun <T> as_o3wada_a0a0a0a0a0a0a0e0d0jb(o: Any, type: Class<T>): T? {
            return (if (type.isInstance(o)) o as T else null)
        }

        private fun <T> as_o3wada_a0a0a1a4a3a53(o: Any, type: Class<T>): T? {
            return (if (type.isInstance(o)) o as T else null)
        }
    }
}
