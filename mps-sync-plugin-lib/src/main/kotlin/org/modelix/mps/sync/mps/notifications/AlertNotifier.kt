package org.modelix.mps.sync.mps.notifications

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import org.modelix.kotlin.utils.UnstableModelixFeature
import java.awt.Color
import javax.swing.JComponent

/**
 * Show an alert dialog to the user with the given severity and content.
 *
 * @property project the active [Project] in MPS to show the dialog in.
 * @property title the title of the alert dialog. Defaults to "Modelix Sync Plugin".
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class AlertNotifier(private val project: Project, private val title: String = "Modelix Sync Plugin") : INotifier {

    override fun error(message: String, responseListener: UserResponseListener?) {
        showAlert(ModelixErrorDialog(project, title, message), responseListener)
    }

    override fun warning(message: String, responseListener: UserResponseListener?) {
        showAlert(ModelixWarningDialog(project, title, message), responseListener)
    }

    override fun info(message: String, responseListener: UserResponseListener?) {
        showAlert(ModelixInfoDialog(project, title, message), responseListener)
    }

    /**
     * Show the alert dialog to the user. If the user accepts the dialog then response listener is called with
     * [UserResponse.USER_ACCEPTED], otherwise with [UserResponse.USER_REJECTED].
     *
     * @param alert the alert dialog to show to the user
     * @param responseListener the listener that waits for the user's reaction / response.
     */
    private fun showAlert(alert: ModelixDialog, responseListener: UserResponseListener?) {
        val isAccepted = alert.showAndGet()
        val response = if (isAccepted) {
            UserResponse.USER_ACCEPTED
        } else {
            UserResponse.USER_REJECTED
        }
        responseListener?.userResponded(response)
        alert.disposeIfNeeded()
    }
}

/**
 * Shows a dialog to the user.
 *
 * @param project the active [Project] in MPS to show the dialog in.
 * @param title the title of the dialog.
 * @param message the message to be shown in the dialog. The text is left-aligned and has the parameter color.
 * @param color the color of the message.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
private abstract class ModelixDialog(project: Project, title: String, message: String, color: Color) :
    DialogWrapper(project) {

    init {
        this.title = title
        this.isModal = true
        this.setResizable(false)

        val formattedMessage = "<html><font color='#$color'><left>$message</left></font><br/></html>"
        this.setErrorText(formattedMessage)

        this.init()
    }

    override fun createCenterPanel(): JComponent? = null
}

/**
 * Shows an error dialog to the user.
 *
 * @param project the active [Project] in MPS to show the dialog in.
 * @param title the title of the dialog.
 * @param message the message to be shown in the dialog. The text is left-aligned and has the
 * [com.intellij.util.ui.JBUI.CurrentTheme.NotificationError.foregroundColor] color.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
private class ModelixErrorDialog(project: Project, title: String, message: String) :
    ModelixDialog(project, title, message, MessageType.ERROR.titleForeground)

/**
 * Shows a warning dialog to the user.
 *
 * @param project the active [Project] in MPS to show the dialog in.
 * @param title the title of the dialog.
 * @param message the message to be shown in the dialog. The text is left-aligned and has the
 * [com.intellij.util.ui.JBUI.CurrentTheme.NotificationWarning.foregroundColor] color.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
private class ModelixWarningDialog(project: Project, title: String, message: String) :
    ModelixDialog(project, title, message, MessageType.WARNING.titleForeground)

/**
 * Shows an info dialog to the user.
 *
 * @param project the active [Project] in MPS to show the dialog in.
 * @param title the title of the dialog.
 * @param message the message to be shown in the dialog. The text is left-aligned and has the
 * [com.intellij.util.ui.JBUI.CurrentTheme.NotificationInfo.foregroundColor] color.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
private class ModelixInfoDialog(project: Project, title: String, message: String) :
    ModelixDialog(project, title, message, MessageType.INFO.titleForeground)
