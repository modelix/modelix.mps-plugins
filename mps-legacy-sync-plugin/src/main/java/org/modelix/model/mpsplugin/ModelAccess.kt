package org.modelix.model.mpsplugin

import jetbrains.mps.project.Project
import jetbrains.mps.project.SModuleOperations
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository

/*Generated by MPS */
object ModelAccess {
    fun runInWriteActionIfNeeded(_this: SModel?, runnable: Runnable) {
        var repo: SRepository? = null
        val module: SModule? = _this!!.module
        if (module != null) {
            val project: Project? = SModuleOperations.getProjectForModule(module)
            if (project != null) {
                repo = project.repository
            }
        }
        if (repo == null) {
            runnable.run()
        } else {
            repo.modelAccess.runWriteAction(object : Runnable {
                override fun run() {
                    runnable.run()
                }
            })
        }
    }
}
