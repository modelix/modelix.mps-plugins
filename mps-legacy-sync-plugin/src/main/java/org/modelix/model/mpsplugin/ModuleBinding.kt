package org.modelix.model.mpsplugin

import jetbrains.mps.internal.collections.runtime.IVisitor
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.MapSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.internal.collections.runtime.SetSequence
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.IWriteTransaction

/*Generated by MPS */
abstract class ModuleBinding(var moduleNodeId: Long, initialSyncDirection: SyncDirection?) :
    Binding(initialSyncDirection) {
    private val treeChangeVisitor: ITreeChangeVisitor = object : ITreeChangeVisitor {
        override fun childrenChanged(nodeId: Long, role: String?) {
            assertSyncThread()
            if (nodeId == moduleNodeId) {
                enqueueSync(SyncDirection.TO_MPS, false, null)
            }
        }

        override fun containmentChanged(nodeId: Long) {}
        override fun referenceChanged(nodeId: Long, role: String) {}
        override fun propertyChanged(nodeId: Long, role: String) {}
    }

    @Suppress("removal")
    private val moduleListener = object : org.jetbrains.mps.openapi.module.SModuleListenerBase() {
        override fun modelAdded(module: SModule, model: SModel) {
            try {
                enqueueSync(SyncDirection.TO_CLOUD, false, null)
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }

        override fun modelRemoved(module: SModule, ref: SModelReference) {
            try {
                enqueueSync(SyncDirection.TO_CLOUD, false, null)
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }

        override fun modelRenamed(module: SModule, model: SModel, oldRef: SModelReference) {
            try {
                enqueueSync(SyncDirection.TO_CLOUD, false, null)
            } catch (ex: Exception) {
                if (LOG.isEnabledFor(Level.ERROR)) {
                    LOG.error("", ex)
                }
            }
        }
    }

    override fun toString(): String {
        return "Module: " + java.lang.Long.toHexString(moduleNodeId) + " -> " + check_rpydrg_a0a0g(
            module, this,
        )
    }

    abstract val module: SModule?
    override fun getTreeChangeVisitor(oldTree: ITree?, newTree: ITree): ITreeChangeVisitor? {
        return treeChangeVisitor
    }

    override fun doActivate() {
        module!!.addModuleListener(moduleListener)
        if (rootBinding.syncQueue.getTask(this) == null) {
            enqueueSync(
                ((if (initialSyncDirection == null) SyncDirection.TO_MPS else initialSyncDirection)!!),
                true,
                null,
            )
        }
    }

    override fun doDeactivate() {
        module!!.removeModuleListener(moduleListener)
    }

    override fun doSyncToMPS(tree: ITree) {
        if (runningTask!!.isInitialSync && Sequence.fromIterable(
                modelsSynchronizer.mPSChildren,
            ).isNotEmpty && Sequence.fromIterable(
                modelsSynchronizer.getCloudChildren(tree),
            ).isEmpty
        ) {
            // TODO remove this workaround
            forceEnqueueSyncTo(SyncDirection.TO_CLOUD, true, null)
            return
        }
        val mappings = modelsSynchronizer.syncToMPS(tree)
        updateBindings(mappings, SyncDirection.TO_MPS)
    }

    override fun doSyncToCloud(t: IWriteTransaction) {
        if (moduleNodeId == 0L) {
            moduleNodeId = ProjectModulesSynchronizer.Companion.createModuleOnCloud(t, module, ITree.ROOT_ID, "modules")
        }
        val mappings: Map<Long, SModel> = modelsSynchronizer.syncToCloud(t)
        updateBindings(mappings, SyncDirection.TO_CLOUD)
    }

    private fun updateBindings(mappings: Map<Long, SModel>, syncDirection: SyncDirection) {
        val bindings: Map<Long, ModelBinding> = MapSequence.fromMap(HashMap())
        Sequence.fromIterable<Binding?>(getOwnedBindings()).ofType<ModelBinding>(
            ModelBinding::class.java,
        ).visitAll(object : IVisitor<ModelBinding>() {
            override fun visit(it: ModelBinding) {
                MapSequence.fromMap(bindings).put(it.modelNodeId, it)
            }
        })
        val toAdd: List<Long?> = SetSequence.fromSet(MapSequence.fromMap(mappings).keys)
            .subtract(SetSequence.fromSet(MapSequence.fromMap(bindings).keys)).toListSequence()
        val toRemove: List<Long> = SetSequence.fromSet(MapSequence.fromMap(bindings).keys)
            .subtract(SetSequence.fromSet(MapSequence.fromMap(mappings).keys)).toListSequence()
        ListSequence.fromList(toRemove).visitAll(object : IVisitor<Long>() {
            override fun visit(it: Long) {
                val binding: ModelBinding? = MapSequence.fromMap(bindings).get(it)
                binding!!.deactivate(null)
                binding.setOwner(null)
            }
        })
        ListSequence.fromList(toAdd).visitAll(object : IVisitor<Long>() {
            override fun visit(it: Long) {
                val binding: ModelBinding = ModelBinding(it, MapSequence.fromMap(mappings).get(it), syncDirection)
                binding.setOwner(this@ModuleBinding)
                binding.activate(null)
            }
        })
    }

    protected open val modelsSynchronizer: Synchronizer<SModel>
        protected get() {
            return ModelsSynchronizer(moduleNodeId, module!!)
        }

    companion object {
        private val LOG: Logger = LogManager.getLogger(ModuleBinding::class.java)
        private fun check_rpydrg_a0a0g(checkedDotOperand: SModule?, checkedDotThisExpression: ModuleBinding): String? {
            if (null != checkedDotOperand) {
                return checkedDotOperand.moduleName
            }
            return null
        }
    }
}
