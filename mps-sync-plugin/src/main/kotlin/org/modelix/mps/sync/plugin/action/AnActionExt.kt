package org.modelix.mps.sync.plugin.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import jetbrains.mps.workbench.MPSDataKeys
import mu.KLogger
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.mps.services.ServiceLocator

/**
 * A helper function to catch any [Throwable] that may occur while we are performing the [action]. If such error occurs,
 * then we inform the user about the cause and show the message to them via the [ServiceLocator.wrappedNotifier].
 *
 * @param event the event from MPS, to fetch the active [Project] from it.
 * @param errorLogger the [KLogger] to be used if an error occurs.
 * @param errorMessagePrefix the prefix of the error message we show to the user.
 * @param action the action we want to perform.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
internal fun actionPerformedSafely(
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

/**
 * @return the [DataKey] for the MPS Context SModel.
 */
fun getMpsContextModelDataKey() = DataKey.create<SModel>("MPS_Context_SModel")

/**
 * @return the [DataKey] for the MPS Context SModule.
 */
fun getMpsContextModuleDataKey() = DataKey.create<SModule>("MPS_Context_SModule")
