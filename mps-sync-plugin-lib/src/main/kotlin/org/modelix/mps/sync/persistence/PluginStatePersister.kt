package org.modelix.mps.sync.persistence

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.IRebindSyncService
import java.io.File

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class PluginStatePersister {

    private val logger = KotlinLogging.logger {}

    fun save(baseDirectory: File, state: PersistableState) {
        try {
            val xmlMapper = XmlMapper()
            xmlMapper.writeValue(getFilePath(baseDirectory), state)
        } catch (t: Throwable) {
            logger.error(t) { "Saving the plugin state failed. Reason: ${t.message}" }
        }
    }

    fun load(baseDirectory: File): PersistableState? {
        try {
            val xmlMapper = XmlMapper()
            return xmlMapper.readValue(getFilePath(baseDirectory), PersistableState::class.java)
        } catch (t: Throwable) {
            logger.error(t) { "Loading the plugin state failed. Reason: ${t.message}" }
            return null
        }
    }

    fun restore(baseDirectory: File, syncService: IRebindSyncService): RestoredStateContext? {
        val state = load(baseDirectory) ?: return null
        state.load(syncService)
        return state.restoredStateContext
    }

    private fun getFilePath(baseDirectory: File) = File(baseDirectory, "syncState.xml")
}
