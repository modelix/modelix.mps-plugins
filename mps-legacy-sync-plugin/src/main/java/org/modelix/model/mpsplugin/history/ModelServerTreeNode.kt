package org.modelix.model.mpsplugin.history

import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.ide.ui.tree.TextTreeNode
import jetbrains.mps.internal.collections.runtime.ISelector
import jetbrains.mps.internal.collections.runtime.IVisitor
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.MapSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SLinkOperations
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SPropertyOperations
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.language.SProperty
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.ITree
import org.modelix.model.area.PArea
import org.modelix.model.mpsplugin.CloudIcons
import org.modelix.model.mpsplugin.ModelServerConnection
import org.modelix.model.mpsplugin.ModelixNotifications
import org.modelix.model.mpsplugin.SharedExecutors
import org.modelix.model.mpsplugin.history.CloudView.CloudViewTree
import javax.swing.SwingUtilities
import javax.swing.tree.TreeNode

/*Generated by MPS */
class ModelServerTreeNode(val modelServer: ModelServerConnection) :
    TextTreeNode(CloudIcons.MODEL_SERVER_ICON, modelServer.baseUrl) {
    private val branchListener: IBranchListener = object : IBranchListener {
        override fun treeChanged(oldTree: ITree?, newTree: ITree) {
            SwingUtilities.invokeLater(object : Runnable {
                override fun run() {
                    (tree as CloudViewTree).runRebuildAction(
                        object : Runnable {
                            override fun run() {
                                updateChildren()
                            }
                        },
                        true,
                    )
                }
            })
        }
    }
    private var infoBranch: IBranch? = null
    private val repoListener: ModelServerConnection.IListener = object : ModelServerConnection.IListener {
        override fun connectionStatusChanged(connected: Boolean) {
            SwingUtilities.invokeLater(object : Runnable {
                override fun run() {
                    if (connected) {
                        infoBranch = modelServer.infoBranch
                        if (tree != null) {
                            infoBranch!!.addListener(branchListener)
                        }
                    }
                    updateText()
                    updateChildren()
                }
            })
        }
    }

    init {
        setAllowsChildren(true)
        nodeIdentifier = "" + System.identityHashCode(modelServer)
        modelServer.addListener(repoListener)
        updateText()
        updateChildren()
    }

    fun updateText() {
        var text: String? = modelServer.baseUrl
        if (modelServer.isConnected) {
            text += " (" + modelServer.id + ")"
        } else {
            text += " (not connected)"
        }
        val email: String? = modelServer.email
        if ((email != null && email.length > 0)) {
            text += " " + email
        }
        setTextAndRepaint(text)
    }

    fun setTextAndRepaint(text: String?) {
        TreeModelUtil.setTextAndRepaint(this, text)
    }

    fun updateChildren() {
        if (modelServer.isConnected) {
            val existing: Map<SNode?, RepositoryTreeNode> =
                MapSequence.fromMap(LinkedHashMap(16, 0.75.toFloat(), false))
            ThreadUtils.runInUIThreadAndWait(object : Runnable {
                override fun run() {
                    if (Sequence.fromIterable<TreeNode?>(TreeModelUtil.getChildren(this@ModelServerTreeNode))
                            .isEmpty
                    ) {
                        TreeModelUtil.setChildren(
                            this@ModelServerTreeNode,
                            Sequence.singleton<TreeNode>(LoadingIcon.Companion.apply<TextTreeNode>(TextTreeNode("loading ..."))),
                        )
                    }
                    for (node: RepositoryTreeNode in Sequence.fromIterable<TreeNode?>(TreeModelUtil.getChildren(this@ModelServerTreeNode))
                        .ofType<RepositoryTreeNode>(
                            RepositoryTreeNode::class.java,
                        )) {
                        MapSequence.fromMap(existing).put(node.repositoryInfo, node)
                    }
                }
            })
            SharedExecutors.FIXED.execute(object : Runnable {
                override fun run() {
                    val newChildren: List<TreeNode>? =
                        PArea(modelServer.infoBranch!!).executeRead<List<TreeNode>>({
                            val info: SNode? = modelServer.info
                            if (info == null) {
                                return@executeRead ListSequence.fromList<TreeNode>(ArrayList<TreeNode>())
                            }
                            ListSequence.fromList<SNode>(SLinkOperations.getChildren(info, LINKS.`repositories$b56J`))
                                .select<TreeNode>(object : ISelector<SNode, TreeNode?>() {
                                    override fun select(it: SNode): TreeNode? {
                                        var tn: TreeNode? = null
                                        try {
                                            tn =
                                                (
                                                    if (MapSequence.fromMap(existing).containsKey(it)) {
                                                        MapSequence.fromMap(
                                                            existing,
                                                        ).get(it)
                                                    } else {
                                                        RepositoryTreeNode(
                                                            modelServer, it,
                                                        )
                                                    }
                                                    )
                                        } catch (t: Throwable) {
                                            t.printStackTrace()
                                            ModelixNotifications.notifyError(
                                                "Repository in invalid state",
                                                "Repository " + SPropertyOperations.getString(
                                                    it,
                                                    PROPS.`id$baYB`,
                                                ) + " cannot be loaded: " + t.message,
                                            )
                                        }
                                        return tn
                                    }
                                }).filterNotNull()
                        })
                    ThreadUtils.runInUIThreadNoWait(object : Runnable {
                        override fun run() {
                            TreeModelUtil.setChildren(this@ModelServerTreeNode, newChildren)
                            Sequence.fromIterable(TreeModelUtil.getChildren(this@ModelServerTreeNode)).ofType(
                                RepositoryTreeNode::class.java,
                            ).visitAll(object : IVisitor<RepositoryTreeNode>() {
                                override fun visit(it: RepositoryTreeNode) {
                                    it.updateChildren()
                                }
                            })
                        }
                    })
                }
            })
        } else {
            ThreadUtils.runInUIThreadNoWait(object : Runnable {
                override fun run() {
                    TreeModelUtil.clearChildren(this@ModelServerTreeNode)
                }
            })
        }
    }

    override fun onAdd() {
        super.onAdd()
        if (infoBranch != null) {
            infoBranch!!.addListener(branchListener)
        }
    }

    override fun onRemove() {
        super.onRemove()
        if (infoBranch != null) {
            modelServer.infoBranch!!.removeListener(branchListener)
        }
    }

    private object LINKS {
        /*package*/
        val `repositories$b56J`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            -0x4967f1420fe2ba63L,
            -0x56adc78bf09cec4cL,
            0x62b7d9b07cecbcbfL,
            0x62b7d9b07cecbcc2L,
            "repositories",
        )
    }

    private object PROPS {
        /*package*/
        val `id$baYB`: SProperty = MetaAdapterFactory.getProperty(
            -0x4967f1420fe2ba63L,
            -0x56adc78bf09cec4cL,
            0x62b7d9b07cecbcc0L,
            0x62b7d9b07cecbcc6L,
            "id",
        )
    }
}
