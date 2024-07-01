package org.modelix.mps.sync.mps.services

import com.intellij.openapi.components.Service
import jetbrains.mps.ide.MPSCoreComponents
import jetbrains.mps.smodel.MPSModuleRepository
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.mpsadapters.MPSLanguageRepository

@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
@Service(Service.Level.APP)
class MPSLanguageRepositoryProvider {

    val mpsLanguageRepository: MPSLanguageRepository

    init {
        // just a dummy call, to initialize the modelix built-in languages
        ILanguageRepository.default.javaClass

        // MPS-internal convention that they use to get the SRepository
        val repository = MPSCoreComponents.getInstance().platform.findComponent(MPSModuleRepository::class.java)
        requireNotNull(repository) { "MPS Module Repository is null. Therefore, Concept lookup will not work." }

        mpsLanguageRepository = MPSLanguageRepository(repository)
        ILanguageRepository.register(mpsLanguageRepository)
    }
}
