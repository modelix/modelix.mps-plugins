/*
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
package org.modelix.ui.diff

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.IgnoreTrailingSlash
import jetbrains.mps.project.MPSProject
import java.io.File
import java.util.Collections

@Service(Service.Level.PROJECT)
class DiffServerForProject(private val project: Project) : Disposable {

    init {
        ApplicationManager.getApplication().getService(DiffServer::class.java).registerProject(project)
    }

    override fun dispose() {
        ApplicationManager.getApplication().getService(DiffServer::class.java).unregisterProject(project)
    }
}

@Service(Service.Level.APP)
class DiffServer : Disposable {

    private var server: EmbeddedServer<*, *>? = null
    private val projects: MutableSet<Project> = Collections.synchronizedSet(HashSet())

    fun registerProject(project: Project) {
        projects.add(project)
        ensureStarted()
    }

    fun unregisterProject(project: Project) {
        projects.remove(project)
    }

    private fun getMPSProjects(): List<MPSProject> {
        return synchronized(projects) {
            projects.mapNotNull { it.getComponent(MPSProject::class.java) }
        }
    }

    fun registerGitRepo(path: File) {
        DiffImages.additionalGitRepos += path
    }

    @Synchronized
    fun ensureStarted() {
        if (server != null) return

        println("starting diff server")

        server = embeddedServer(Netty, port = 33334) {
            initKtorServer(this)
        }

        server!!.start()
    }

    fun initKtorServer(application: Application) {
        application.apply {
            install(IgnoreTrailingSlash)
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Post)
            }
            install(DiffHandler)
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    when (cause) {
                        else -> {
                            val text = """
                                    |500: $cause
                                    |
                                    |${cause.stackTraceToString()}
                            """.trimMargin()
                            call.respondText(text = text, status = HttpStatusCode.InternalServerError)
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    fun ensureStopped() {
        if (server == null) return
        println("stopping modelix server")
        server?.stop()
        server = null
    }

    override fun dispose() {
        ensureStopped()
    }
}

class DiffServerStartupActivity : StartupActivity.Background {
    override fun runActivity(project: Project) {
        project.service<DiffServerForProject>() // just ensure it's initialized
    }
}
