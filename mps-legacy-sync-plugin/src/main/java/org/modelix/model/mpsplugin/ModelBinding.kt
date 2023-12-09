package org.modelix.model.mpsplugin

import gnu.trove.procedure.TLongProcedure
import gnu.trove.set.hash.TLongHashSet
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.internal.collections.runtime.SetSequence
import jetbrains.mps.smodel.SModelInternal
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.event.SModelChildEvent
import jetbrains.mps.smodel.event.SModelDevKitEvent
import jetbrains.mps.smodel.event.SModelImportEvent
import jetbrains.mps.smodel.event.SModelLanguageEvent
import jetbrains.mps.smodel.event.SModelListener
import jetbrains.mps.smodel.event.SModelListener.SModelListenerPriority
import jetbrains.mps.smodel.event.SModelPropertyEvent
import jetbrains.mps.smodel.event.SModelReferenceEvent
import jetbrains.mps.smodel.event.SModelRenamedEvent
import jetbrains.mps.smodel.event.SModelRootEvent
import jetbrains.mps.smodel.loading.ModelLoadingState
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import org.jetbrains.mps.openapi.event.SNodeAddEvent
import org.jetbrains.mps.openapi.event.SNodeRemoveEvent
import org.jetbrains.mps.openapi.event.SPropertyChangeEvent
import org.jetbrains.mps.openapi.event.SReferenceChangeEvent
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeChangeListener
import org.modelix.model.api.IBranch
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.ITreeChangeVisitorEx
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.area.PArea
import org.modelix.model.operations.RoleInNode
import java.util.Objects

/*Generated by MPS */
class ModelBinding(val modelNodeId: Long, val model: SModel?, initialSyncDirection: SyncDirection?) :
    Binding(initialSyncDirection) {
    private val childrenSyncToMPSRequired: Set<RoleInNode> = SetSequence.fromSet(HashSet())
    private val referenceSyncToMPSRequired: Set<RoleInNode> = SetSequence.fromSet(HashSet())
    private val propertySyncToMPSRequired: Set<RoleInNode> = SetSequence.fromSet(HashSet())
    private val fullNodeSyncToMPSRequired: TLongHashSet = TLongHashSet()
    private var modelPropertiesSyncToMPSRequired: Boolean = true
    private var synchronizer: ModelSynchronizer? = null
    private val nodeChangeListener: SNodeChangeListener = object : SNodeChangeListener {
        override fun propertyChanged(e: SPropertyChangeEvent) {
            try {
                if (isSynchronizing) {
                    return
                }
                val branch: IBranch? = this@ModelBinding.branch
                PArea((branch)!!).executeWrite({
                    synchronizer!!.runAndFlushReferences(object : Runnable {
                        override fun run() {
                            val t: IWriteTransaction = branch.writeTransaction
                            val id: Long = synchronizer!!.getOrSyncToCloud(e.node, t)
                            if (id != 0L && t.containsNode(id)) {
                                t.setProperty(id, e.property.name, e.newValue)
                            }
                        }
                    })
                })
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }

        override fun referenceChanged(e: SReferenceChangeEvent) {
            try {
                if (isSynchronizing) {
                    return
                }
                synchronizer!!.runAndFlushReferences(object : Runnable {
                    override fun run() {
                        synchronizer!!.handleReferenceChanged(e)
                    }
                })
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }

        override fun nodeAdded(e: SNodeAddEvent) {
            try {
                if (isSynchronizing) {
                    return
                }
                synchronizer!!.runAndFlushReferences(object : Runnable {
                    override fun run() {
                        synchronizer!!.handleMPSNodeAdded(e)
                    }
                })
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }

        override fun nodeRemoved(e: SNodeRemoveEvent) {
            try {
                if (isSynchronizing) {
                    return
                }
                synchronizer!!.runAndFlushReferences(object : Runnable {
                    override fun run() {
                        synchronizer!!.handleMPSNodeRemoved(e)
                    }
                })
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }
    }
    private val modelListener: SModelListener = object : SModelListener {
        override fun languageAdded(event: SModelLanguageEvent) {
            try {
                if (isSynchronizing) {
                    return
                }
                synchronizer!!.runAndFlushReferences(object : Runnable {
                    override fun run() {
                        synchronizer!!.syncUsedLanguagesAndDevKitsFromMPS()
                    }
                })
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }

        override fun languageRemoved(event: SModelLanguageEvent) {
            try {
                if (isSynchronizing) {
                    return
                }
                synchronizer!!.runAndFlushReferences(object : Runnable {
                    override fun run() {
                        synchronizer!!.syncUsedLanguagesAndDevKitsFromMPS()
                    }
                })
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }

        override fun devkitAdded(event: SModelDevKitEvent) {
            try {
                if (isSynchronizing) {
                    return
                }
                synchronizer!!.runAndFlushReferences(object : Runnable {
                    override fun run() {
                        synchronizer!!.syncUsedLanguagesAndDevKitsFromMPS()
                    }
                })
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }

        override fun devkitRemoved(event: SModelDevKitEvent) {
            try {
                if (isSynchronizing) {
                    return
                }
                synchronizer!!.runAndFlushReferences(object : Runnable {
                    override fun run() {
                        synchronizer!!.syncUsedLanguagesAndDevKitsFromMPS()
                    }
                })
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }

        override fun beforeChildRemoved(event: SModelChildEvent) {}
        override fun beforeModelDisposed(model: SModel) {}
        override fun beforeModelRenamed(event: SModelRenamedEvent) {}
        override fun beforeRootRemoved(event: SModelRootEvent) {}
        override fun childAdded(event: SModelChildEvent) {}
        override fun childRemoved(event: SModelChildEvent) {}
        override fun getPriority(): SModelListenerPriority {
            return SModelListenerPriority.CLIENT
        }

        override fun importAdded(event: SModelImportEvent) {
            try {
                if (isSynchronizing) {
                    return
                }
                synchronizer!!.runAndFlushReferences(object : Runnable {
                    override fun run() {
                        synchronizer!!.syncModelImportsFromMPS()
                    }
                })
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }

        override fun importRemoved(event: SModelImportEvent) {
            try {
                if (isSynchronizing) {
                    return
                }
                synchronizer!!.runAndFlushReferences(object : Runnable {
                    override fun run() {
                        synchronizer!!.syncModelImportsFromMPS()
                    }
                })
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }

        override fun modelLoadingStateChanged(model: SModel, state: ModelLoadingState) {}
        override fun modelRenamed(event: SModelRenamedEvent) {}
        override fun modelSaved(model: SModel) {}
        override fun propertyChanged(event: SModelPropertyEvent) {}
        override fun referenceAdded(event: SModelReferenceEvent) {}
        override fun referenceRemoved(event: SModelReferenceEvent) {}

        @Deprecated("")
        override fun rootAdded(event: SModelRootEvent) {
        }

        @Deprecated("")
        override fun rootRemoved(event: SModelRootEvent) {
        }
    }

    override fun toString(): String {
        return "Model: " + java.lang.Long.toHexString(modelNodeId) + " -> " + model!!.name.value
    }

    override fun doActivate() {
        synchronizer = ModelSynchronizer(modelNodeId, model, cloudRepository!!)
        model!!.addChangeListener(nodeChangeListener)
        (model as SModelInternal?)!!.addModelListener(modelListener)
    }

    override fun doDeactivate() {
        model!!.removeChangeListener(nodeChangeListener)
        (model as SModelInternal?)!!.removeModelListener(modelListener)
        synchronizer = null
    }

    override fun doSyncToCloud(t: IWriteTransaction) {
        synchronizer!!.fullSyncFromMPS()
    }

    override fun doSyncToMPS(tree: ITree) {
        if (runningTask!!.isInitialSync) {
            val mpsRootNodes: Iterable<SNode> = model!!.rootNodes
            val cloudRootNodes: Iterable<Long> = tree.getChildren(modelNodeId, LINKS.`rootNodes$jxXY`.name)
            if (Sequence.fromIterable(mpsRootNodes).isNotEmpty && Sequence.fromIterable(cloudRootNodes).isEmpty) {
                // TODO remove this workaround
                forceEnqueueSyncTo(SyncDirection.TO_CLOUD, true, null)
            } else {
                synchronizer!!.syncModelToMPS(tree, false)
            }
        } else {
            synchronizer!!.runAndFlushReferences(object : Runnable {
                override fun run() {
                    for (roleInNode: RoleInNode in SetSequence.fromSet(childrenSyncToMPSRequired)) {
                        try {
                            if (tree.containsNode(roleInNode.nodeId)) {
                                synchronizer!!.syncChildrenToMPS(roleInNode.nodeId, roleInNode.role, tree, false)
                            }
                        } catch (ex: Exception) {
                            if (LOG.isEnabledFor(Level.ERROR)) {
                                LOG.error("", ex)
                            }
                        }
                    }
                    SetSequence.fromSet(childrenSyncToMPSRequired).clear()
                    for (roleInNode: RoleInNode in SetSequence.fromSet(referenceSyncToMPSRequired)) {
                        try {
                            if (tree.containsNode(roleInNode.nodeId)) {
                                synchronizer!!.syncReferenceToMPS(roleInNode.nodeId, roleInNode.role, tree)
                            }
                        } catch (ex: Exception) {
                            if (LOG.isEnabledFor(Level.ERROR)) {
                                LOG.error("", ex)
                            }
                        }
                    }
                    SetSequence.fromSet(referenceSyncToMPSRequired).clear()
                    for (roleInNode: RoleInNode in SetSequence.fromSet(propertySyncToMPSRequired)) {
                        try {
                            if (tree.containsNode(roleInNode.nodeId)) {
                                synchronizer!!.syncPropertyToMPS(roleInNode.nodeId, roleInNode.role, tree)
                            }
                        } catch (ex: Exception) {
                            if (LOG.isEnabledFor(Level.ERROR)) {
                                LOG.error("", ex)
                            }
                        }
                    }
                    SetSequence.fromSet(propertySyncToMPSRequired).clear()
                    fullNodeSyncToMPSRequired.forEach(object : TLongProcedure {
                        override fun execute(nodeId: Long): Boolean {
                            try {
                                if (tree.containsNode(nodeId)) {
                                    synchronizer!!.syncNodeToMPS(nodeId, tree, true)
                                }
                            } catch (ex: Exception) {
                                if (LOG.isEnabledFor(Level.ERROR)) {
                                    LOG.error("", ex)
                                }
                            }
                            return true
                        }
                    })
                    fullNodeSyncToMPSRequired.clear()
                    if (modelPropertiesSyncToMPSRequired) {
                        try {
                            synchronizer!!.syncModelPropertiesToMPS(tree)
                        } catch (ex: Exception) {
                            if (LOG.isEnabledFor(Level.ERROR)) {
                                LOG.error("", ex)
                            }
                        }
                    }
                }
            })
            modelPropertiesSyncToMPSRequired = false
        }
    }

    override fun getTreeChangeVisitor(oldTree: ITree?, newTree: ITree): ITreeChangeVisitor {
        return object : ITreeChangeVisitorEx {
            fun isInsideModel(nodeId: Long): Boolean {
                assertSyncThread()
                val parent: Long = newTree.getParent(nodeId)
                if (parent == 0L) {
                    return false
                }
                if (parent == modelNodeId) {
                    return Objects.equals(newTree.getRole(nodeId), LINKS.`rootNodes$jxXY`.name)
                }
                return isInsideModel(parent)
            }

            fun isInsideModelOrModel(nodeId: Long): Boolean {
                assertSyncThread()
                if (nodeId == modelNodeId) {
                    return true
                }
                return isInsideModel(nodeId)
            }

            fun isModelProperties(nodeId: Long): Boolean {
                assertSyncThread()
                val parent: Long = newTree.getParent(nodeId)
                if (parent == 0L) {
                    return false
                }
                if (parent == modelNodeId) {
                    return !(Objects.equals(newTree.getRole(nodeId), LINKS.`rootNodes$jxXY`.name))
                }
                return isModelProperties(parent)
            }

            override fun containmentChanged(nodeId: Long) {}
            override fun childrenChanged(nodeId: Long, role: String?) {
                assertSyncThread()
                if (modelNodeId == nodeId) {
                    if (Objects.equals(role, LINKS.`rootNodes$jxXY`.name)) {
                        SetSequence.fromSet(childrenSyncToMPSRequired).addElement(RoleInNode(nodeId, role))
                    } else {
                        modelPropertiesSyncToMPSRequired = true
                    }
                } else if (isModelProperties(nodeId)) {
                    modelPropertiesSyncToMPSRequired = true
                } else if (isInsideModel(nodeId)) {
                    SetSequence.fromSet(childrenSyncToMPSRequired).addElement(RoleInNode(nodeId, role))
                }
                enqueueSync(SyncDirection.TO_MPS, false, null)
            }

            override fun referenceChanged(nodeId: Long, role: String) {
                assertSyncThread()
                if (isModelProperties(nodeId)) {
                    modelPropertiesSyncToMPSRequired = true
                    enqueueSync(SyncDirection.TO_MPS, false, null)
                    return
                }
                if (!(isInsideModel(nodeId))) {
                    return
                }
                SetSequence.fromSet(referenceSyncToMPSRequired).addElement(RoleInNode(nodeId, role))
                enqueueSync(SyncDirection.TO_MPS, false, null)
            }

            override fun propertyChanged(nodeId: Long, role: String) {
                assertSyncThread()
                if (isModelProperties(nodeId)) {
                    modelPropertiesSyncToMPSRequired = true
                    enqueueSync(SyncDirection.TO_MPS, false, null)
                    return
                }
                if (!(isInsideModel(nodeId))) {
                    return
                }
                SetSequence.fromSet(propertySyncToMPSRequired).addElement(RoleInNode(nodeId, role))
                enqueueSync(SyncDirection.TO_MPS, false, null)
            }

            override fun nodeRemoved(nodeId: Long) {}
            override fun nodeAdded(nodeId: Long) {
                assertSyncThread()
                if (!(isInsideModel(nodeId))) {
                    return
                }
                fullNodeSyncToMPSRequired.add(nodeId)
                enqueueSync(SyncDirection.TO_MPS, false, null)
            }
        }
    }

    private object LINKS {
        /*package*/
        val `rootNodes$jxXY`: SContainmentLink = MetaAdapterFactory.getContainmentLink(
            0xa7577d1d4e5431dL,
            -0x674e051c70651180L,
            0x69652614fd1c50cL,
            0x69652614fd1c514L,
            "rootNodes",
        )
    }

    companion object {
        private val LOG: Logger = LogManager.getLogger(ModelBinding::class.java)
    }
}
