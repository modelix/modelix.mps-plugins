package org.modelix.mps.sync.modelix.util

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.PropertyFromName

/**
 * Some custom properties of [BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency] that are not part of the
 * official concept definition.
 */
object ModuleDependencyConstants {
    /**
     * Shows if the [BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency] represents a read-only dependency. I.e.
     * it refers to a [BuiltinLanguages.MPSRepositoryConcepts.Module] that is read-only in MPS.
     */
    val MODULE_DEPENDENCY_IS_READ_ONLY_PROPERTY = PropertyFromName("isReadOnly")
}
