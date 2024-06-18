package org.modelix.mps.sync.plugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import jetbrains.mps.workbench.MPSDataKeys
import mu.KLogger
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.mps.services.ServiceLocator

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal fun AnAction.actionPerformedSafely(
    event: AnActionEvent,
    errorLogger: KLogger,
    errorMessagePrefix: String,
    action: (ServiceLocator) -> Unit,
) {
    var serviceLocator: ServiceLocator? = null

    try {
        val project = event.getData(MPSDataKeys.PROJECT)
        checkNotNull(project) { "Action is not possible, because Project is null." }
        serviceLocator = project.service<ServiceLocator>()

        action(serviceLocator)
    } catch (t: Throwable) {
        val message = "$errorMessagePrefix Cause: ${t.message}"

        val notifier = serviceLocator?.wrappedNotifier
        if (notifier == null) {
            errorLogger.error(t) { message }
        } else {
            notifier.notifyAndLogError(message, t, errorLogger)
        }
    }
}
