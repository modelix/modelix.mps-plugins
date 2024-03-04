package org.modelix.mps.generator.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.header
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import jetbrains.mps.smodel.MPSModuleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.br
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.html
import kotlinx.html.id
import kotlinx.html.meta
import kotlinx.html.pre
import kotlinx.html.stream.createHTML
import kotlinx.html.style
import kotlinx.html.table
import kotlinx.html.td
import kotlinx.html.tr
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun GeneratorOutputHandler(generator: AsyncGenerator) = createApplicationPlugin("DiffHandler") {
    val handler = GeneratorOutputHandlerImpl(generator)
    application.routing {
        handler.apply { installRoutes() }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class GeneratorOutputHandlerImpl(val generator: AsyncGenerator) {

    fun Route.installRoutes() {
        get {
            val repository = getMPSRepository()
            val modules: List<Pair<String, String>> = repository.modelAccess.computeRead {
                repository.modules.map { (it.moduleName ?: "") to it.moduleId.toString() }.toList()
            }
            call.respondHtmlSafe {
                body {
                    div {
                        +"Choose a module for generation:"
                    }
                    br { }
                    for (module in modules) {
                        div {
                            a(href = "modules/" + URLEncoder.encode(module.second, StandardCharsets.UTF_8) + "/") {
                                +module.first
                            }
                        }
                    }
                }
            }
        }
        get("modules/{moduleId}/") {
            val moduleIdStr = call.parameters["moduleId"]!!
            val moduleId = PersistenceFacade.getInstance().createModuleId(moduleIdStr)
            val repository = getMPSRepository()
            val models: List<Pair<String, String>>? = repository.modelAccess.computeRead {
                val module = repository.getModule(moduleId)
                module?.models?.map { (it.name.value) to it.modelId.toString() }?.toList()
            }
            if (models == null) {
                call.respondText("Module not found: $moduleIdStr", status = HttpStatusCode.NotFound)
                return@get
            }
            call.respondHtmlSafe {
                body {
                    div {
                        +"Choose a model for generation:"
                    }
                    br { }
                    for (module in models) {
                        div {
                            a(href = "models/" + URLEncoder.encode(module.second, StandardCharsets.UTF_8) + "/generatorOutput/") {
                                +module.first
                            }
                        }
                    }
                }
            }
        }
        get("generatorOutput") {
            val modelRefStr = call.request.queryParameters["modelRef"]
            if (modelRefStr == null) {
                call.respondText("modelRef parameter missing", status = HttpStatusCode.BadRequest)
            } else {
                val modelRef = PersistenceFacade.getInstance().createModelReference(modelRefStr)
                call.respondRedirect(url = "modules/${modelRef.moduleReference?.moduleId}/models/${modelRef.modelId}/generatorOutput/")
            }
        }
        route("/modules/{moduleId}/models/{modelId}/generatorOutput/") {
            fun PipelineContext<*, ApplicationCall>.getModel(): SModel {
                val moduleIdStr = call.parameters["moduleId"]!!
                val modelIdStr = call.parameters["modelId"]!!
                val moduleId = PersistenceFacade.getInstance().createModuleId(moduleIdStr)
                val moduleRef = PersistenceFacade.getInstance().createModuleReference(moduleId, "")
                val modelId = PersistenceFacade.getInstance().createModelId(modelIdStr)
                val modelRef = PersistenceFacade.getInstance().createModelReference(moduleRef, modelId, "")

                val repository = getMPSRepository()
                return repository.modelAccess.computeRead { modelRef.resolve(repository) }
            }

            get("download") {
                val model = getModel()
                val generatorOutput: AsyncGenerator.GeneratorOutput = generator.getTextGenOutput(model)
                val files = getFilesIfCompleted(generatorOutput) ?: return@get
                call.response.header("Content-Disposition", """attachment; filename="${model.name.longName}.zip"""")
                call.respondOutputStream(contentType = ContentType.Application.Zip) {
                    val zip = ZipOutputStream(this)
                    for (file in files) {
                        zip.putNextEntry(ZipEntry(file.name))
                        zip.write(file.content)
                    }
                    zip.finish()
                }
            }

            route("/files/{fileName}/") {
                suspend fun PipelineContext<*, ApplicationCall>.getFile(): AsyncGenerator.GeneratedFile? {
                    val fileName = call.parameters["fileName"]!!
                    val generatorOutput: AsyncGenerator.GeneratorOutput = generator.getTextGenOutput(getModel())
                    val file = generatorOutput.outputFiles.await().firstOrNull { it.name == fileName }
                    if (file == null) {
                        call.respondText("File not found: $fileName", status = HttpStatusCode.NotFound)
                    }
                    return file
                }

                get("download") {
                    val file = getFile() ?: return@get
                    call.response.header("Content-Disposition", """attachment; filename="${file.name}"""")
                    call.respondText(file.text)
                }
                get("view") {
                    val file = getFile() ?: return@get
                    call.respondText(file.text)
                }
            }

            get {
                val generatorOutput: AsyncGenerator.GeneratorOutput = generator.getTextGenOutput(getModel())
                val files = getFilesIfCompleted(generatorOutput) ?: return@get
                call.respondHtmlSafe {
                    body {
                        generateFullOutput(generatorOutput)
                    }
                }
            }
        }
    }

    private fun getMPSRepository(): MPSModuleRepository {
        @Suppress("removal")
        return MPSModuleRepository.getInstance()
    }

    private suspend fun PipelineContext<Unit, ApplicationCall>.getFilesIfCompleted(output: AsyncGenerator.GeneratorOutput): List<AsyncGenerator.GeneratedFile>? {
        return if (output.outputFiles.isActive) {
            call.respondHtmlSafe {
                head {
                    meta {
                        httpEquiv = "refresh"
                        content = "5"
                    }
                }
                body {
                    div {
                        +"Generating..."
                    }
                    br { }
                    div {
                        messagesToHtml(output)
                    }
                }
            }
            null
        } else if (output.outputFiles.isCompleted) {
            output.outputFiles.getCompleted()
        } else {
            call.respondHtmlSafe {
                body {
                    div {
                        +"Generation failed"
                    }
                    div {
                        messagesToHtml(output)
                    }
                    output.outputFiles.getCompletionExceptionOrNull()?.let { ex ->
                        pre {
                            +ex.stackTraceToString()
                        }
                    }
                }
            }
            null
        }
    }

    fun FlowContent.generateFullOutput(generatorOutput: AsyncGenerator.GeneratorOutput) {
        val generatedFiles = generatorOutput.outputFiles.getCompleted()

        h1 {
            +"Output for model "
            +generatorOutput.inputModel.name?.value.toString()
        }
        div {
            a(href = "download") {
                +"Download all files as ZIP-archive"
            }
        }
        table {
            generatedFiles.forEach { file ->
                tr {
                    td {
                        a(href = "#" + URLEncoder.encode(file.name, StandardCharsets.UTF_8)) {
                            +file.name
                        }
                    }
                    td {
                        a(href = "files/${URLEncoder.encode(file.name, StandardCharsets.UTF_8)}/view") {
                            +"View"
                        }
                    }
                    td {
                        a(href = "files/${URLEncoder.encode(file.name, StandardCharsets.UTF_8)}/download") {
                            +"Download"
                        }
                    }
                }
            }
        }
        br { }
        br { }
        messagesToHtml(generatorOutput)
        br { }
        br { }
        generatedFiles.forEach { file ->
            h2 {
                id = file.name
                +file.name
            }
            pre {
                style = "border:1px solid white"
                +file.text.let {
                    val limit = 100000
                    if (it.length > limit) {
                        it.substring(0..limit) + "... (" + (it.length - limit) + " more characters)"
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun FlowContent.messagesToHtml(output: AsyncGenerator.GeneratorOutput) {
        output.messages.forEach { message ->
            div(classes = "generator-message-${message.kind}") {
                +message.text
            }
        }
        output.finalMessages.forEach { message ->
            div {
                +message
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
