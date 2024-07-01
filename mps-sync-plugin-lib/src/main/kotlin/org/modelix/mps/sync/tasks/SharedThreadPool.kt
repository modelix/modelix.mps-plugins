package org.modelix.mps.sync.tasks

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import org.modelix.kotlin.utils.UnstableModelixFeature
import java.util.concurrent.Executors

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Service(Service.Level.APP)
class SharedThreadPool : Disposable {

    val threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    override fun dispose() {
        threadPool.shutdownNow()
    }
}
