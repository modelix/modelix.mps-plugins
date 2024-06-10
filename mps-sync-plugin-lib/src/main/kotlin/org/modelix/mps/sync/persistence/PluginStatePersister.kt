package org.modelix.mps.sync.persistence

import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.IRebindModulesSyncService
import java.io.File
import java.nio.file.Files

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class PluginStatePersister {

    private val logger = KotlinLogging.logger {}

    private val serializer = PersistableStateSerializer()

    fun save(baseDirectory: File, state: PersistableState) {
        try {
            val serialized = Json.encodeToString(serializer, state)
            val targetFile = getFilePath(baseDirectory)
            Files.writeString(targetFile, serialized)
        } catch (t: Throwable) {
            logger.error(t) { "Saving the plugin state failed. Cause: ${t.message}" }
        }
    }

    fun load(baseDirectory: File): PersistableState? {
        try {
            val sourceFile = getFilePath(baseDirectory)
            val serialized = Files.readString(sourceFile)
            return Json.decodeFromString(serializer, serialized)
        } catch (t: Throwable) {
            logger.error(t) { "Loading the plugin state failed. Cause: ${t.message}" }
            return null
        }
    }

    fun restore(baseDirectory: File, syncService: IRebindModulesSyncService): RestoredStateContext? {
        val state = load(baseDirectory) ?: return null
        return state.restoreState(syncService)
    }

    private fun getFilePath(baseDirectory: File) = baseDirectory.resolve("syncState.json").toPath()
}
