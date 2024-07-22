package org.modelix.mps.sync.plugin.util

import jetbrains.mps.project.MPSProject

internal fun <R> MPSProject.runRead(body: () -> R): R {
    var result: R? = null
    modelAccess.runReadAction {
        result = body()
    }
    return checkNotNull(result)
}

internal fun <R> MPSProject.executeCommand(body: () -> R): R {
    var result: R? = null
    modelAccess.executeCommand {
        result = body()
    }
//    modelAccess.runWriteAction {
//        result = body()
//    }
    return checkNotNull(result)
}

// TODO Olekz remove if needed

/*
    protected fun <R> runCommandOnEDT(body: () -> R): R {
        var result: R? = null
        val exception = ThreadUtils.runInUIThreadAndWait {
            mpsProject.modelAccess.executeCommand {
                result = body()
            }
        }
        if (exception != null) {
            throw exception
        }
        checkNotNull(result) {
            "The result was null even those no exception was thrown."
        }
        return result as R
    }

 */
