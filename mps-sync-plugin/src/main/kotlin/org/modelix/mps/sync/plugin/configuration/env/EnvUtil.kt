package org.modelix.mps.sync.plugin.configuration.env

import com.intellij.openapi.application.ApplicationManager

const val MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_ENABLED = "MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_ENABLED"
const val MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_URL = "MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_URL"
const val MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_REPOSITORY = "MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_REPOSITORY"
const val MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_BRANCH = "MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_BRANCH"
const val MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_JWT = "MODELIX_SYNC_PLUGIN_AUTOMATIC_MODE_JWT"

fun getEnv(key: String): String? =
    if (ApplicationManager.getApplication().isUnitTestMode) {
        // TODO Olekz make properties an addition for tests
        // Use system properties for tests because they can be modified in unit tests.
        System.getProperty(key)
    } else {
        System.getenv(key)
    }

fun getEnvRequired(env: String): String {
    val value = getEnv(env)
    check(!value.isNullOrBlank()) {
        "Value for $env required."
    }
    return value
}

fun getBooleanStrictOrElse(env: String, defaultValue: Boolean): Boolean {
    val value: String = getEnv(env) ?: return defaultValue

    return when (value) {
        "true" -> true
        "false" -> false
        else -> {
            val message = "Value `$value` for $env cannot be parsed as boolean. Valid values are `true` and `false`"
            throw throw IllegalStateException(message)
        }
    }
}
