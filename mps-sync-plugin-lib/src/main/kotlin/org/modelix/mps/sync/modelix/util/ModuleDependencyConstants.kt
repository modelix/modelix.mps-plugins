package org.modelix.mps.sync.modelix.util

import org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Module
import org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency
import org.modelix.model.api.PropertyFromName

/**
 * Some custom properties of [ModuleDependency] that are not part of the official concept definition.
 */
object ModuleDependencyConstants {
    /**
     * Shows if the [ModuleDependency] represents a read-only dependency. I.e. it refers to a [Module] that is read-only
     * in MPS.
     */
    val MODULE_DEPENDENCY_IS_READ_ONLY_PROPERTY = PropertyFromName("isReadOnly")
}
