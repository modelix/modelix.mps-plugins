package org.modelix.mps.sync.mps.notifications

import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
interface INotifier {

    fun error(message: String, responseListener: UserResponseListener? = null)

    fun warning(message: String, responseListener: UserResponseListener? = null)

    fun info(message: String, responseListener: UserResponseListener? = null)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun interface UserResponseListener {

    fun userResponded(result: String)
}
