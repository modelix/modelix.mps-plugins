package org.modelix.mps.sync.mps.notifications

interface INotifier {

    fun error(message: String, responseListener: UserResponseListener? = null)

    fun warning(message: String, responseListener: UserResponseListener? = null)

    fun info(message: String, responseListener: UserResponseListener? = null)
}

fun interface UserResponseListener {

    fun userResponded(result: String)
}
