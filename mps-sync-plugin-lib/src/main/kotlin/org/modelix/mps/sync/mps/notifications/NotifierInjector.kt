package org.modelix.mps.sync.mps.notifications

import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object NotifierInjector {
    var notifier: INotifier = NoOpNotifier()
}
