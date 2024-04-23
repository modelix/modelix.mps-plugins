package org.modelix.mps.sync.mps.notifications

import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.modelix.kotlin.utils.UnstableModelixFeature
import javax.swing.event.HyperlinkEvent

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class BalloonNotifier(
    private val project: Project,
    private val title: String = "Modelix Sync Plugin",
    groupName: String = "Modelix Sync Plugin",
) : INotifier {

    private val notificationGroup = NotificationGroup.create(
        displayId = groupName,
        displayType = NotificationDisplayType.STICKY_BALLOON,
        isLogByDefault = true,
        toolWindowId = null,
        icon = null,
        pluginId = null,
        title = null,
    )

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
        val adapter = UrlListenerToUserResponseAdapter(responseListener)
        notificationGroup.createNotification(title, message, notificationType, adapter).notify(project)
    }
}

private class UrlListenerToUserResponseAdapter(
    private val userResponseListener: UserResponseListener?,
) : NotificationListener {

    override fun hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
        userResponseListener?.userResponded(event.description)
    }
}
