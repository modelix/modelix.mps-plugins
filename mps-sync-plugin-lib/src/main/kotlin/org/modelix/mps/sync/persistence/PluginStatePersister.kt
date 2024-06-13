package org.modelix.mps.sync.persistence

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.IRebindModulesSyncService
import java.io.File

/**
 * This is a utility class that saves, loads and restores a [PersistableState] to and from an XML file.
 *
 * @param providedFile the File in which the class will be saved. If it's a directory, then a fill will be created in it with name [defaultFileName].
 * @param defaultFileName the file name to be used if [providedFile] is a directory
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class PluginStatePersister(providedFile: File, defaultFileName: String = "syncState.xml") {

    private val logger = KotlinLogging.logger {}

    private val targetFile: File = if (providedFile.isDirectory) {
        providedFile.resolve(defaultFileName)
    } else {
        providedFile
    }

    /**
     * Saves the state into the XML file.
     *
     * @param state the [PersistableState] we want to save
     */
    fun save(state: PersistableState) {
        try {
            XmlMapper().writeValue(targetFile, state)
        } catch (t: Throwable) {
            logger.error(t) { "Saving the plugin state failed. Cause: ${t.message}" }
        }
    }

    /**
     * Loads the state from the XML file.
     *
     * @param baseDirectory the folder from which we will load the serialized [PersistableState]
     *
     * @return the deserialized [PersistableState]
     */
    fun load(baseDirectory: File): PersistableState? {
        return try {
            XmlMapper().readValue(targetFile, PersistableState::class.java)
        } catch (t: Throwable) {
            logger.error(t) { "Loading the plugin state failed. Cause: ${t.message}" }
            null
        }
    }

    /**
     * Loads the state from the XML file and then initializes the internal state of the modelix sync lib via
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
}
