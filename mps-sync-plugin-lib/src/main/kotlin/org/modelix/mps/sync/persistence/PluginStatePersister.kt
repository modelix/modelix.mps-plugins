package org.modelix.mps.sync.persistence

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.intellij.openapi.project.Project
import mu.KotlinLogging
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.IRebindModulesSyncService
import java.io.File

/**
 * This is a utility class that saves, loads and restores a [PersistableState] to and from an XML file.
 *
 * @param providedFile the File in which the class will be saved. If it's a directory, then a fill will be created in it
 * with name defaultFileName.
 * @param defaultFileName (optional) the file name to be used if providedFile is a directory. Defaults to
 * [PluginStatePersister.DEFAULT_FILE_NAME].
 */
@UnstableModelixFeature(
    reason = "The new modelix MPS plugin is under construction",
    intendedFinalization = "This feature is finalized when the new sync plugin is ready for release.",
)
class PluginStatePersister(providedFile: File, defaultFileName: String? = null) {

    companion object {
        /**
         * The default name of the XML file.
         */
        const val DEFAULT_FILE_NAME = "modelixSyncPluginState.xml"
    }

    /**
     * Just a normal logger to log messages.
     */
    private val logger = KotlinLogging.logger {}

    private val targetFile: File = if (providedFile.isDirectory) {
        providedFile.resolve(defaultFileName ?: DEFAULT_FILE_NAME)
    } else {
        providedFile
    }

    /**
     * Saves the state into the XML file.
     *
     * @param state the [PersistableState] we want to save.
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
     * @return the deserialized [PersistableState].
     */
    fun load(): PersistableState? {
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
     * @param syncService see [PersistableState.restoreState].
     * @param project the [Project] that is opened in MPS.
     *
     * @return see [PersistableState.restoreState].
     */
    fun restore(syncService: IRebindModulesSyncService, project: Project): RestoredStateContext? {
        val state = load() ?: return null
        return state.restoreState(syncService, project)
    }
}
