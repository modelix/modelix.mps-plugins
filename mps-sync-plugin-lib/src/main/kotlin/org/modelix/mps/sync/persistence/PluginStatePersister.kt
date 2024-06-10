package org.modelix.mps.sync.persistence

import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.IRebindModulesSyncService
import java.io.File
import java.nio.file.Files

/**
 * This is a utility class that saves, loads and restores a [PersistableState] to and from a JSON file.
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class PluginStatePersister {

    private val logger = KotlinLogging.logger {}

    private val serializer = PersistableStateSerializer()

    /**
     * Saves the state into a JSON file. The full path of the file is defined by [PluginStatePersister.getFilePath].
     *
     * @param baseDirectory the folder into which we want to save the `state`
     * @param state the [PersistableState] we want to save
     */
    fun save(baseDirectory: File, state: PersistableState) {
        try {
            val serialized = Json.encodeToString(serializer, state)
            val targetFile = getFilePath(baseDirectory)
            Files.writeString(targetFile, serialized)
        } catch (t: Throwable) {
            logger.error(t) { "Saving the plugin state failed. Cause: ${t.message}" }
        }
    }

    /**
     * Loads the state from a JSON file. The full path of the file is defined by [PluginStatePersister.getFilePath].
     *
     * @param baseDirectory the folder from which we will load the serialized [PersistableState]
     *
     * @return the deserialized [PersistableState]
     */
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

    /**
     * Loads the state from a JSON file and then initializes the internal state of the modelix sync lib via
     * [PersistableState.restoreState].
     *
     * @param baseDirectory the folder from which we will load the serialized [PersistableState]
     * @param syncService see [PersistableState.restoreState]
     *
     * @return see [PersistableState.restoreState]
     */
    fun restore(baseDirectory: File, syncService: IRebindModulesSyncService): RestoredStateContext? {
        val state = load(baseDirectory) ?: return null
        return state.restoreState(syncService)
    }

    private fun getFilePath(baseDirectory: File) = baseDirectory.resolve("syncState.json").toPath()
}
