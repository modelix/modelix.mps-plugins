package org.modelix.ui.diff

import com.intellij.openapi.project.ProjectManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.project.MPSProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.html
import kotlinx.html.img
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import java.util.Collections
import javax.imageio.ImageIO

val DiffHandler = createApplicationPlugin("DiffHandler") {
    val handler = DiffHandlerImpl()
    application.routing {
        handler.apply { installRoutes() }
    }
}

data class DiffRequest(val leftRevision: String, val rightRevision: String)

class DiffHandlerImpl() {
    private val diffRequests = Collections.synchronizedMap(HashMap<DiffRequest, Deferred<List<DiffImage>>>())
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    fun Route.installRoutes() {
        get("/") {
            call.respondText("Diff server")
        }
        get("/clear") {
            diffRequests.clear()
            call.respondText("Cache cleared")
        }
        route("/{leftRevision}/{rightRevision}") {
            suspend fun PipelineContext<Unit, ApplicationCall>.getImages(): Deferred<List<DiffImage>>? {
                val diffRequest = DiffRequest(call.parameters["leftRevision"]!!, call.parameters["rightRevision"]!!)

                var diffResult = diffRequests[diffRequest]
                if (diffResult == null) {
                    val project = (ProjectManager.getInstance().openProjects + ProjectManager.getInstance().defaultProject).firstOrNull()
                    if (project == null) {
                        call.respondText(text = "No project found", status = HttpStatusCode.ServiceUnavailable)
                        return null
                    }
                    val mpsProject: MPSProject? = ProjectHelper.fromIdeaProject(project)
                    if (mpsProject == null) {
                        call.respondText(text = "MPS project not initialized yet", status = HttpStatusCode.ServiceUnavailable)
                        return null
                    }

                    diffResult = synchronized(diffRequests) {
                        if (!diffRequests.containsKey(diffRequest)) {
                            diffRequests[diffRequest] = coroutineScope.async {
                                DiffImages(project)
                                    .diffRevisions(diffRequest.leftRevision, diffRequest.rightRevision).flatMap {
                                        // The computation of the diff is not allowed to happen on the EDT,
                                        // but the rendering of the diff dialog has to happen on the EDT.
                                        //
                                        // Using Dispatchers.Swing instead of Dispatchers.Main, because it's not
                                        // initialized in older MPS versions.
                                        withContext(Dispatchers.Swing) {
                                            it()
                                        }
                                    }
                            }
                        }
                        diffRequests[diffRequest]
                    }
                }
                return diffResult
            }
            get("/") {
                val images0 = getImages() ?: return@get
                val images = images0.await()
                call.respondHtmlSafe {
                    body {
                        h1 { +"Diff" }
                        br { }
                        br { }
                        for (image in images) {
                            h2 {
                                text(image.affectedFile + " - " + image.rootNodePresentation)
                            }
                            div {
                                img(src = image.id + ".png") {
                                    style = "height:auto;max-width:100%;width:${image.size.width}px"
                                }
                                br { }
                                br { }
                                br { }
                            }
                        }
                    }
                }
            }
            get("{imageId}.png") {
                val imageId = call.parameters["imageId"]
                val images = getImages() ?: return@get
                val image = images.await().find { it.id == imageId }
                if (image == null) {
                    call.respondText("Image with ID $imageId not found", status = HttpStatusCode.NotFound)
                } else {
                    call.respondOutputStream(contentType = ContentType.Image.PNG) {
                        ImageIO.write(image.image, "png", this)
                    }
                }
            }
        }
    }
}

/**
 * respondHtml fails to respond anything if an exception is thrown in the body and an error handler is installed
 * that tries to respond an error page.
 */
suspend fun ApplicationCall.respondHtmlSafe(status: HttpStatusCode = HttpStatusCode.OK, block: HTML.() -> Unit) {
    val htmlText = createHTML().html {
        block()
    }
    respondText(htmlText, ContentType.Text.Html, status)
}
