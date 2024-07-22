package org.modelix.mps.sync.plugin.configuration

import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.plugin.configuration.env.MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_BRANCH
import org.modelix.mps.sync.plugin.configuration.env.MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_ENABLED
import org.modelix.mps.sync.plugin.configuration.env.MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_JWT
import org.modelix.mps.sync.plugin.configuration.env.MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_REPOSITORY
import org.modelix.mps.sync.plugin.configuration.env.MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_URL
import org.modelix.mps.sync.plugin.configuration.env.getBooleanStrictOrElse
import org.modelix.mps.sync.plugin.configuration.env.getEnv
import org.modelix.mps.sync.plugin.configuration.env.getEnvRequired
import java.net.MalformedURLException
import java.net.URL

sealed interface PluginMode {
    data object Interactive : PluginMode
    data class Automatic(val configuration: AutomaticModeConfig) : PluginMode

    companion object {
        fun getPluginMode(): PluginMode {
            if (!getBooleanStrictOrElse(MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_ENABLED, false)) {
                return Interactive
            }
            val url = getAutomaticModeURL()
            val repositoryId = getAutomaticModeRepositoryId()
            val branchReference = getAutomaticModeBranchId(repositoryId)
            val jwt = getEnv(MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_JWT)
            val configuration = AutomaticModeConfig(url, branchReference, jwt)
            return Automatic(configuration)
        }

        private fun getAutomaticModeURL(): URL {
            val urlValue = getEnvRequired(MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_URL)
            val url = try {
                URL(urlValue)
            } catch (e: MalformedURLException) {
                val message = "Value `$urlValue` for $MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_URL cannot be parsed as URL."
                throw IllegalStateException(message, e)
            }
            return url
        }

        private fun getAutomaticModeRepositoryId(): RepositoryId {
            val repositoryName = getEnvRequired(MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_REPOSITORY)
            val repositoryId = try {
                RepositoryId(repositoryName)
            } catch (e: IllegalArgumentException) {
                val message = "Value `$repositoryName` for $MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_REPOSITORY" +
                    " cannot be parsed as repository ID."
                throw IllegalStateException(message, e)
            }
            return repositoryId
        }

        private fun getAutomaticModeBranchId(repositoryId: RepositoryId): BranchReference {
            val branchName = getEnvRequired(MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_BRANCH)
            val branchReference = try {
                repositoryId.getBranchReference(branchName)
            } catch (e: IllegalArgumentException) {
                val message =
                    "Value `$branchName` for $MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_BRANCH cannot be parsed as branch ID."
                throw IllegalStateException(message, e)
            }
            return branchReference
        }
    }
}

// TODO Olekz do not print initial jwt in to string.
data class AutomaticModeConfig(var modelServerUrl: URL, var branch: BranchReference, val jwt: String?)
