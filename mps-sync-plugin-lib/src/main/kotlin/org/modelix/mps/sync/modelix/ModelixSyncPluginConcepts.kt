package org.modelix.mps.sync.modelix

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.BuiltinLanguages.MPSRepositoryConcepts.Module
import org.modelix.model.api.BuiltinLanguages.jetbrains_mps_lang_core
import org.modelix.model.api.SimpleChildLink
import org.modelix.model.api.SimpleConcept
import org.modelix.model.api.SimpleLanguage
import org.modelix.model.api.SimpleReferenceLink

object ModelixSyncPluginConcepts :
    SimpleLanguage("org.modelix.model.syncpluginconcepts", uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee81") {

    override var includedConcepts =
        arrayOf(ReadonlyModel, ReadonlyModule, ReadonlyModuleDependency, ReadonlyModelReference, ReadonlyModelNode)

    object ReadonlyModel : SimpleConcept(
        conceptName = "ReadonlyModel",
        uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee81/474657388638618893",
        directSuperConcepts = listOf(BuiltinLanguages.MPSRepositoryConcepts.Model),
    ) {
        init {
            addConcept(this)
        }
    }

    object ReadonlyModule : SimpleConcept(
        conceptName = "ReadonlyModule",
        uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee81/474657388638618896",
        directSuperConcepts = listOf(Module),
    ) {
        init {
            addConcept(this)
        }

        val readonlyModels = SimpleChildLink(
            simpleName = "readonlyModels",
            isMultiple = true,
            isOptional = true,
            isOrdered = false,
            targetConcept = ReadonlyModel,
            uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee81/474657388638618896/474657388638618899",
        ).also(this::addChildLink)
    }

    object ReadonlyModuleDependency : SimpleConcept(
        conceptName = "ReadonlyModuleDependency",
        uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee81/2206727074858242416",
        directSuperConcepts = listOf(BuiltinLanguages.MPSRepositoryConcepts.ModuleDependency),
    ) {

        init {
            addConcept(this)
        }
    }

    object ReadonlyModelReference : SimpleConcept(
        conceptName = "ReadonlyModelReference",
        uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee81/6402965165736932004",
        directSuperConcepts = listOf(BuiltinLanguages.MPSRepositoryConcepts.ModelReference),
    ) {
        init {
            addConcept(this)
        }

        val readonlyModel = SimpleReferenceLink(
            simpleName = "readonlyModel",
            isOptional = false,
            targetConcept = ReadonlyModel,
            uid = "0a7577d1-d4e5-431d-98b1-fae38f9aee81/6402965165736932004/6402965165736932005",
        )
    }

    object ReadonlyModelNode : SimpleConcept(
        conceptName = "ReadonlyModelNode",
        uid = "mps:0a7577d1-d4e5-431d-98b1-fae38f9aee81/6402965165736932005",
        directSuperConcepts = listOf(jetbrains_mps_lang_core.BaseConcept),
    ) {
        init {
            addConcept(this)
        }
    }
}
