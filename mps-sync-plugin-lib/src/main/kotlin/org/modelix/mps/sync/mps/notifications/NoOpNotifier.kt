package org.modelix.mps.sync.mps.notifications

import org.modelix.kotlin.utils.UnstableModelixFeature

/**
 * An empty notifier that does not do anything.
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class NoOpNotifier : INotifier {

    /**
     * Does nothing.
     *
     * @see [INotifier.error].
     */
    override fun error(message: String, responseListener: UserResponseListener?) {}

    /**
     * Does nothing.
     *
     * @see [INotifier.warning].
     */
    override fun warning(message: String, responseListener: UserResponseListener?) {}

    /**
     * Does nothing.
     *
     * @see [INotifier.info].
     */
    override fun info(message: String, responseListener: UserResponseListener?) {}
}
