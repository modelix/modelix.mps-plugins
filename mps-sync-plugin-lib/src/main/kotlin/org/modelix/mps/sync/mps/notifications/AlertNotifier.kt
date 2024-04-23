package org.modelix.mps.sync.mps.notifications

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageType
import org.modelix.kotlin.utils.UnstableModelixFeature
import java.awt.Color
import javax.swing.JComponent

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
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

    private fun showAlert(alert: ModelixDialog, responseListener: UserResponseListener?) {
        val result = alert.showAndGet()
        responseListener?.userResponded(result.toString())
        alert.disposeIfNeeded()
    }
}

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

private class ModelixErrorDialog(project: Project, title: String, message: String) :
    ModelixDialog(project, title, message, MessageType.ERROR.titleForeground)

private class ModelixWarningDialog(project: Project, title: String, message: String) :
    ModelixDialog(project, title, message, MessageType.WARNING.titleForeground)

private class ModelixInfoDialog(project: Project, title: String, message: String) :
    ModelixDialog(project, title, message, MessageType.INFO.titleForeground)
