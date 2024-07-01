package org.modelix.mps.sync.mps.services

import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class SyncPreferences : InjectableService {

    var synchronizeReadonlyModulesAndModels = false
}
