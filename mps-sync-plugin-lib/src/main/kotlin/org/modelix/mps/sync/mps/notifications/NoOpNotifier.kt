package org.modelix.mps.sync.mps.notifications

import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class NoOpNotifier : INotifier {
    override fun error(message: String, responseListener: UserResponseListener?) {}

    override fun warning(message: String, responseListener: UserResponseListener?) {}

    override fun info(message: String, responseListener: UserResponseListener?) {}
}
