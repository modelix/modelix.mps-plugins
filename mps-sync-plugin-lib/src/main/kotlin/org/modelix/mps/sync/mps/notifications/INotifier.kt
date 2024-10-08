package org.modelix.mps.sync.mps.notifications

import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * Show a message to the user with the given severity and let the user react to this message.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
interface INotifier {

    /**
     * Show an error to the user with the given message to which the user can react.
     *
     * @param message the message to be shown to the user.
     * @param responseListener a listener that waits for the user's reaction / response. When the listener is called,
     * depends on the implementation of the [INotifier].
     */
    fun error(message: String, responseListener: UserResponseListener? = null)

    /**
     * Show a warning to the user with the given message to which the user can react.
     *
     * @param message the message to be shown to the user.
     * @param responseListener a listener that waits for the user's reaction / response. When the listener is called,
     * depends on the implementation of the [INotifier].
     */
    fun warning(message: String, responseListener: UserResponseListener? = null)

    /**
     * Show an info to the user with the given message to which the user can react.
     *
     * @param message the message to be shown to the user.
     * @param responseListener a listener that waits for the user's reaction / response. When the listener is called,
     * depends on the implementation of the [INotifier].
     */
    fun info(message: String, responseListener: UserResponseListener? = null)
}

/**
 * A listener to listen to the user's response.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
fun interface UserResponseListener {

    /**
     * The event-handler is called if the user has responded.
     *
     * @param response the response of the user.
     */
    fun userResponded(response: UserResponse)
}

/**
 * The possible responses of the user:
 *  - USER_ACCEPTED: the user accepted the message.
 *  - USER_REJECTED: the user rejected the message.
 *  - UNSPECIFIED: the user reacted in another way to the message.
 */
enum class UserResponse {
    USER_ACCEPTED,
    USER_REJECTED,
    UNSPECIFIED, // expand this enum for other possible values
}
