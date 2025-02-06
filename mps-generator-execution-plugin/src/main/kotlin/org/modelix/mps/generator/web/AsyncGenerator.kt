package org.modelix.mps.generator.web

import jetbrains.mps.extapi.model.GeneratableSModel
import jetbrains.mps.internal.collections.runtime.Sequence
import jetbrains.mps.internal.make.runtime.util.FutureValue
import jetbrains.mps.make.IMakeNotificationListener
import jetbrains.mps.make.IMakeService
import jetbrains.mps.make.MakeSession
import jetbrains.mps.make.dependencies.MakeSequence
import jetbrains.mps.make.facet.FacetRegistry
import jetbrains.mps.make.facet.IFacet
import jetbrains.mps.make.facet.ITarget
import jetbrains.mps.make.resources.IResource
import jetbrains.mps.make.script.IResult
import jetbrains.mps.make.script.IResult.FAILURE
import jetbrains.mps.make.script.IScript
import jetbrains.mps.make.script.IScriptController
import jetbrains.mps.make.script.IScriptController.Stub2
import jetbrains.mps.make.script.ScriptBuilder
import jetbrains.mps.make.service.AbstractMakeService
import jetbrains.mps.make.service.CoreMakeTask
import jetbrains.mps.messages.IMessage
import jetbrains.mps.messages.IMessageHandler
import jetbrains.mps.messages.Message
import jetbrains.mps.messages.MessageKind
import jetbrains.mps.project.Project
import jetbrains.mps.smodel.resources.ModelsToResources
import jetbrains.mps.text.TextGenResult
import jetbrains.mps.text.TextUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.module.ModelAccess
import org.jetbrains.mps.openapi.util.ProgressMonitor
import org.modelix.mps.api.ModelixMpsApi
import java.lang.reflect.Constructor
import java.util.Collections
import java.util.concurrent.Future

class AsyncGenerator {
    /**
     * Dispatchers.IO, because the coroutine is waiting for the result of a Future which blocks the thread.
     */
    private val executor: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val cache: MutableMap<SModelReference, GeneratorOutput> = Collections.synchronizedMap(HashMap())

    @Synchronized
    fun getTextGenOutput(model: SModel): GeneratorOutput {
        var generationResult: GeneratorOutput? = cache[model.reference]
        val modelHash: String?
        if (generationResult != null) {
            if (generationResult.outputFiles.isActive) {
                return generationResult
            }
            modelHash = computeModelHash(model)
            if (modelHash == generationResult.modelHash) {
                return generationResult
            }
        } else {
            modelHash = computeModelHash(model)
        }

        val messages = Collections.synchronizedList(ArrayList<IMessage>())
        val finalMessages = Collections.synchronizedList(ArrayList<String>())

        val generatedFiles: Deferred<List<GeneratedFile>> = executor.async {
            // most of the following code is similar to the one in
            // https://github.com/JetBrains/MPS/blob/master/plugins/mps-make/source_gen/jetbrains/mps/ide/make/actions/TextPreviewModel_Action.java

            val project = ModelixMpsApi.getMPSProject() as Project
            val textGenName =
                resolveFacetName("jetbrains.mps.make.facets.TextGen", "jetbrains.mps.lang.core.TextGen")
            val scr: IScript =
                ScriptBuilder(project.getComponent<FacetRegistry>(FacetRegistry::class.java))
                    .withFacetNames(
                        resolveFacetName("jetbrains.mps.make.facets.Generate", "jetbrains.mps.lang.core.Generate"),
                        textGenName,
                        resolveFacetName("jetbrains.mps.make.facets.Make", "jetbrains.mps.lang.core.Make"),
                    ).withFinalTarget(ITarget.Name("$textGenName.textGenToMemory"))
                    .toScript()
            val messageHandler: IMessageHandler = object : IMessageHandler {
                override fun handle(message: IMessage) {
                    messages.add(message)
                }
            }
            val session = MakeSession(project, messageHandler, true)
            val makeService: IMakeService = MyMakeService()
            val future: Future<IResult> = makeService.make(
                session,
                ModelsToResources(Sequence.singleton(model)).canGenerateCondition { true }.resources(),
                scr,
            )
            val result = future.get()

            // .message is only available since MPS 2023.2
            // finalMessages.add(result.message())

            // TextGenOutcomeResource is part of an MPS module which we can't use from an IDEA plugin.
            // Using reflection as a workaround.
            val output = result.output().filter { it::class.java.simpleName == "TextGenOutcomeResource" }
            val textUnits = output.flatMap {
                val textGenResult = it::class.java.getMethod("getTextGenResult").invoke(it) as TextGenResult
                textGenResult.units
            }
            return@async textUnits.map { GeneratedFile(it) }
        }
        generationResult = GeneratorOutput(
            model.reference,
            modelHash,
            generatedFiles,
            messages,
            finalMessages,
        )
        cache[model.reference] = generationResult

        return generationResult
    }

    private fun resolveFacetName(vararg alternativeNames: String): IFacet.Name {
        // Some facets where moved to a different package in MPS 2022.3
        // See https://github.com/JetBrains/MPS/commit/e15a11d4b5e84ff3372365cdab832c56b68b7050
        // To make the plugin compatible with version before and after the renaming we have to look up the correct name.
        val facetRegistry = ModelixMpsApi.getMPSProjects()
            .filterIsInstance<Project>()
            .mapNotNull { it.getComponent(FacetRegistry::class.java) }
            .first()
        val resolvedFacet = alternativeNames.asSequence().mapNotNull { facetRegistry.lookup(IFacet.Name(it)) }.firstOrNull()
        return requireNotNull(resolvedFacet) {
            "None of the facet names found: $alternativeNames"
        }.name
    }

    private fun computeModelHash(model: SModel): String? {
        return ModelixMpsApi.getRepository().modelAccess.computeRead {
            (model as? GeneratableSModel)?.modelHash
        }
    }

    private inner class MyMakeService : AbstractMakeService() {
        override fun make(
            session: MakeSession,
            resources: Iterable<IResource>?,
            script: IScript?,
            controller: IScriptController?,
            monitor: ProgressMonitor,
        ): Future<IResult> {
            val scrName = "Build"
            if (Sequence.fromIterable(resources).isEmpty()) {
                val msg = "$scrName aborted: nothing to do"
                session.getMessageHandler().handle(Message(MessageKind.ERROR, msg))
                return FutureValue<IResult>(FAILURE(null))
            }
            val makeSeq: MakeSequence = MakeSequence(resources, script, session)
            val ctl: IScriptController = this.completeController(controller, session)
            val task: CoreMakeTask = createCoreMakeTask(makeSeq, ctl, session.getMessageHandler())
            task.run(monitor)
            return FutureValue(task.getResult())
        }

        private fun completeController(ctl: IScriptController?, makeSession: MakeSession): IScriptController {
            if (ctl != null) {
                return ctl
            }
            return Stub2(makeSession)
        }

        override fun addListener(listener: IMakeNotificationListener) {
            throw UnsupportedOperationException()
        }

        override fun removeListener(listener: IMakeNotificationListener) {
            throw UnsupportedOperationException()
        }

        override fun closeSession(session: MakeSession) {
        }

        override fun isSessionActive(): Boolean = false

        override fun openNewSession(session: MakeSession): Boolean {
            return false
        }
    }

    class GeneratedFile(unit: TextUnit) {
        var name: String = unit.getFileName()
        var content: ByteArray = unit.getBytes()
        private val encoding = unit.getEncoding()
        val text: String
            get() = String(content, encoding)
    }

    class GeneratorOutput(
        val inputModel: SModelReference,
        val modelHash: String?,
        val outputFiles: Deferred<List<GeneratedFile>>,
        var messages: List<IMessage>,
        val finalMessages: List<String>,
    )
}

fun <R> ModelAccess.computeRead(body: () -> R): R {
    var result: R? = null
    this.runReadAction {
        result = body()
    }
    return result as R
}

/**
 * Using reflection because of a breaking change in MPS.
 */
private fun createCoreMakeTask(makeSeq: MakeSequence, ctl: IScriptController, mh: IMessageHandler): CoreMakeTask {
    val taskConstructor = CoreMakeTask::class.java.firstConstructor()
    val parameterValues = taskConstructor.parameterTypes.map {
        when (it) {
            String::class.java -> ""
            MakeSequence::class.java -> makeSeq
            IScriptController::class.java -> ctl
            IMessageHandler::class.java -> mh
            else -> error("Unexpected parameter: $it")
        }
    }.toTypedArray()
    return taskConstructor.newInstance(*parameterValues)
}

@Suppress("UNCHECKED_CAST")
private fun <T> Class<T>.firstConstructor() = CoreMakeTask::class.java.constructors.first() as Constructor<T>
