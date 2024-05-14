package org.modelix.mps.sync.mps.notifications

import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
interface INotifier {

    fun error(message: String, responseListener: UserResponseListener? = null)

    fun warning(message: String, responseListener: UserResponseListener? = null)

    fun info(message: String, responseListener: UserResponseListener? = null)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.")
fun interface UserResponseListener {

    fun userResponded(response: UserResponse)
}

enum class UserResponse {
    USER_ACCEPTED,
    USER_REJECTED,
    UNSPECIFIED, // expand this enum for other possible values
}
