/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.WaitFor
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.runBlocking
import org.apache.log4j.Logger
import org.modelix.mps.sync.plugin.util.executeCommand
import org.modelix.mps.sync.plugin.util.runRead
import java.nio.file.Path
import java.util.Collections

@Suppress("removal")
abstract class SyncPluginTestBase : HeavyPlatformTestCase() {
    protected lateinit var projectDir: Path
    val mpsProject: MPSProject
        get() {
            return checkNotNull(ProjectHelper.fromIdeaProject(project)) { "MPS project not loaded" }
        }
    val loggedErrorsDuringStartup: MutableList<LoggedError> = Collections.synchronizedList(mutableListOf())
    private val byKeyOldPropertyValues = mutableMapOf<String, String?>()

    /**
     * Works together with [org.modelix.mps.sync.plugin.configuration.env.getEnv] to set configuration for tests.
     * Will not work when running tests in parallel.
     * When running tests in parallel, a different solution will be needed.
     */
    open fun getByKeyCustomEnvValue(): Map<String, String> {
        return emptyMap()
    }

    override fun setUp() {
        fun setCustomEnvValues() {
            getByKeyCustomEnvValue().forEach { (key, newValue) ->
                val oldValue = System.getProperty(key)
                byKeyOldPropertyValues[key] = oldValue
                System.setProperty(key, newValue)
            }
        }
        executeCatchingStartupError {
            setCustomEnvValues()
            super.setUp()
        }
    }

    override fun tearDown() {
        fun resetCustomEnvValues() {
            byKeyOldPropertyValues.forEach { (key, oldValue) ->
                if (oldValue == null) {
                    System.clearProperty(key)
                } else {
                    System.setProperty(key, oldValue)
                }
            }
        }
        try {
            super.tearDown()
        } finally {
            resetCustomEnvValues()
        }
    }

    override fun isCreateDirectoryBasedProject(): Boolean = true

    data class LoggedError(val message: String?, val t: Throwable?, val details: List<String>?)

    // TODO Olekz Try out background activity.
    // TODO Olekz explain, why we do this.
    private fun executeCatchingStartupError(block: () -> Unit) {
        LoggedErrorProcessor.setNewInstance(object : LoggedErrorProcessor() {
            override fun processError(message: String?, t: Throwable?, details: Array<out String>?, logger: Logger) {
                println("Encountered error at startup: $message")
                t?.printStackTrace()
                loggedErrorsDuringStartup.add(LoggedError(message, t, details?.toList()))
            }
        })
        try {
            block()
        } finally {
            LoggedErrorProcessor.restoreDefaultProcessor()
        }
    }
}

internal fun waitUntil(
    exceptionMessage: String? = "Waited too long.",
    timeoutMilliseconds: Int = 10_000,
    step: Int = 100,
    conditionBlock: suspend () -> Boolean,
) {
    val waitFor = object : WaitFor(timeoutMilliseconds, step) {
        override fun condition() = runBlocking {
            conditionBlock()
        }
    }
    waitFor.assertCompleted("Timed out waiting until: $exceptionMessage")
}

// TODO Olekz replace `runReadMps` with `com.intellij.openapi.application.runReadAction`
internal fun <R> SyncPluginTestBase.runReadMps(body: () -> R) = mpsProject.runRead(body)
internal fun <R> SyncPluginTestBase.executeCommandMps(body: () -> R) = mpsProject.executeCommand(body)
