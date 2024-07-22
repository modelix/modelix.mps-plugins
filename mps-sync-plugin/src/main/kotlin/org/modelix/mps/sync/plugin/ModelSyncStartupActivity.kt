@file:OptIn(UnstableModelixFeature::class)

package org.modelix.mps.sync.plugin

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.plugin.automatic.AutomaticSyncService
import org.modelix.mps.sync.plugin.configuration.AutomaticModeConfig
import org.modelix.mps.sync.plugin.configuration.PluginMode
import org.modelix.mps.sync.plugin.configuration.PluginMode.Companion.getPluginMode

private val LOG = logger<ModelSyncStartupActivity>()

// TODO Olekz add ticket to drop support for 2020.3
// TODO Use `ProjectActivity` after dropping support for older MPS versions
/**
 * Entry point to the Modelix model sync plugin.
 * Configures synchronization.
 *
 * Implement [StartupActivity.DumbAware] because we do not need the smart mode to set up synchronization.
 */
class ModelSyncStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        // TODO Olekz SIMPLIFY by not running this in unit-test-mode
        // TODO Olekz Disable gui
        val pluginMode = getPluginMode()
        LOG.info("Plugin mode uses configuration: `$pluginMode`.")
        when (pluginMode) {
            is PluginMode.Automatic -> startAutomaticMode(project, pluginMode.configuration)
            is PluginMode.Interactive -> startInteractiveMode()
        }
    }

    private fun startAutomaticMode(project: Project, config: AutomaticModeConfig) {
        project.service<AutomaticSyncService>().setupAutomaticSync(config)
    }

    private fun startInteractiveMode() {
        // XXX The interactive mode is currently configured from within [ModelSyncGui].
        // In the future, the loading of state and setup of synchronization should be done here.
        // The [ModelSyncGui] should only display and update state.
    }
}
