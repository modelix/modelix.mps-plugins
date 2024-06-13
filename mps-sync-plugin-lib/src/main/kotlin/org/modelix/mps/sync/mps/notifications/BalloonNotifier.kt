package org.modelix.mps.sync.mps.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.apache.log4j.Level
import org.modelix.kotlin.utils.UnstableModelixFeature
import javax.swing.event.HyperlinkEvent

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
class BalloonNotifier(
    private val project: Project,
    private val title: String = "Modelix Sync Plugin",
    groupName: String = "Modelix Sync Plugin",
) : INotifier {

    private val notificationGroup: NotificationGroup

    init {
        // manually set the log level to ERROR instead of INFO, to avoid the confusing "Notification group is already registered" log message from NotificationGroup
        logger<NotificationGroup>().setLevel(Level.ERROR)
        notificationGroup = NotificationGroup.balloonGroup(groupName)
    }

    override fun error(message: String, responseListener: UserResponseListener?) =
        showNotification(message, NotificationType.ERROR, responseListener)

    override fun warning(message: String, responseListener: UserResponseListener?) =
        showNotification(message, NotificationType.WARNING, responseListener)

    override fun info(message: String, responseListener: UserResponseListener?) =
        showNotification(message, NotificationType.INFORMATION, responseListener)

    private fun showNotification(
        message: String,
        notificationType: NotificationType,
        responseListener: UserResponseListener?,
    ) {
        val notification = notificationGroup.createNotification(title, message, notificationType)
        val adapter = UrlListenerToUserResponseAdapter(responseListener)
        notification.setListener(adapter)
        notification.notify(project)
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
private class UrlListenerToUserResponseAdapter(
    private val userResponseListener: UserResponseListener?,
) : NotificationListener {

    override fun hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
        userResponseListener?.userResponded(UserResponse.UNSPECIFIED)
    }
}
