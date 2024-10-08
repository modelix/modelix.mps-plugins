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

/**
 * Shows a [balloon notification](https://jetbrains.github.io/ui/controls/balloon/) in MPS.
 *
 * @param groupName the notification group name. Defaults to "Modelix Sync Plugin".
 *
 * @property project the active [Project] in MPS to show the notification in.
 * @property title the title of the notification. Defaults to "Modelix Sync Plugin".
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class BalloonNotifier(
    private val project: Project,
    private val title: String = "Modelix Sync Plugin",
    groupName: String = "Modelix Sync Plugin",
) : INotifier {

    /**
     * The group to which this notifier is registered.
     */
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

    /**
     * Shows the balloon notification with the given severity. [UrlListenerToUserResponseAdapter] is used to call the
     * response listener, if there is a hyperlink in the message, and the user clicked on it.
     *
     * @param message the message to be shown in the balloon.
     * @param notificationType the type of the notification.
     * @param responseListener the listener that waits for the user's reaction / response.
     */
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

/**
 * Calls the [UserResponseListener] if the user clicked on a hyperlink in the message.
 *
 * @property userResponseListener the listener to be called if the user clicked on a hyperlink in the message.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
private class UrlListenerToUserResponseAdapter(
    private val userResponseListener: UserResponseListener?,
) : NotificationListener {

    override fun hyperlinkUpdate(notification: Notification, event: HyperlinkEvent) {
        userResponseListener?.userResponded(UserResponse.UNSPECIFIED)
    }
}
