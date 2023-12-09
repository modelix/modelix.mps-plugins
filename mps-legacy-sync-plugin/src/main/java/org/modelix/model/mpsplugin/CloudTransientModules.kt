package org.modelix.model.mpsplugin

import jetbrains.mps.baseLanguage.closures.runtime.Wrappers
import jetbrains.mps.baseLanguage.closures.runtime.Wrappers._T
import jetbrains.mps.extapi.module.SRepositoryExt
import jetbrains.mps.internal.collections.runtime.ListSequence
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.project.ModuleId
import jetbrains.mps.smodel.MPSModuleOwner
import jetbrains.mps.smodel.MPSModuleRepository
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.log4j.Logger
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModuleId

/*Generated by MPS */
class CloudTransientModules(private val mpsRepository: SRepositoryExt) {
    private val modules: List<CloudTransientModule?> = ListSequence.fromList(ArrayList())
    private val moduleOwner: MPSModuleOwner = object : MPSModuleOwner {
        public override fun isHidden(): Boolean {
            return false
        }
    }

    fun isModuleIdUsed(moduleId: SModuleId?): Boolean {
        val result: Wrappers._boolean = Wrappers._boolean()
        mpsRepository.getModelAccess().runReadAction(object : Runnable {
            public override fun run() {
                result.value = mpsRepository.getModule((moduleId)!!) != null
            }
        })
        return result.value
    }

    fun createModule(name: String?, id: ModuleId): CloudTransientModule? {
        val module: _T<CloudTransientModule?> = _T(null)
        mpsRepository.getModelAccess().runWriteAction(object : Runnable {
            public override fun run() {
                module.value = CloudTransientModule(name, id)
                ListSequence.fromList(modules).addElement(module.value)
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Register module " + id)
                }
                mpsRepository.registerModule(module.value!!, moduleOwner)
            }
        })
        return module.value
    }

    fun disposeModule(module: CloudTransientModule?) {
        mpsRepository.getModelAccess().runWriteAction(object : Runnable {
            public override fun run() {
                doDisposeModule(module)
                ListSequence.fromList(modules).removeElement(module)
            }
        })
    }

    protected fun doDisposeModule(module: CloudTransientModule?) {
        if (module!!.getRepository() != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unregister module " + module.getModuleId())
            }
            mpsRepository.unregisterModule((module), moduleOwner)
        }
        val models: Iterable<SModel> = module.getModels()
        for (model: CloudTransientModel in Sequence.fromIterable(models).ofType(
            CloudTransientModel::class.java
        )) {
            model.dispose()
        }
    }

    fun dispose() {
        WriteAccessUtil.runWrite(mpsRepository, object : Runnable {
            public override fun run() {
                try {
                    for (module: CloudTransientModule? in ListSequence.fromList(modules)) {
                        doDisposeModule(module)
                    }
                    ListSequence.fromList(modules).clear()
                } catch (ex: Exception) {
                    if (LOG.isEnabledFor(Level.ERROR)) {
                        LOG.error("", ex)
                    }
                }
            }
        })
    }

    companion object {
        private val LOG: Logger = LogManager.getLogger(CloudTransientModules::class.java)
        val instance: CloudTransientModules = CloudTransientModules(MPSModuleRepository.getInstance())
    }
}