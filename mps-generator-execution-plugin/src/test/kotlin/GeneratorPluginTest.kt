import jetbrains.mps.ide.project.ProjectHelper
import org.modelix.mps.generator.web.AsyncGenerator
import org.modelix.mps.generator.web.computeRead
import kotlin.time.ExperimentalTime

class GeneratorPluginTest : GeneratorPluginTestBase() {

    @OptIn(ExperimentalTime::class)
    fun testAsyncGenerator() = kotlinx.coroutines.test.runTest {
        val generator = AsyncGenerator()
        val mpsProject = checkNotNull(ProjectHelper.fromIdeaProject(project)) { "MPSProject is null" }
        val repository = mpsProject.repository
        val model = repository.modelAccess.computeRead {
            // just some random model
            val module = repository.modules.first { it.moduleName == "jetbrains.mps.baseLanguage.tuples.runtime" }
            module.models.first { it.name.longName == "jetbrains.mps.baseLanguage.tuples.runtime" }
        }
        val expected = repository.modelAccess.computeRead { model.rootNodes.count() }
        val output = generator.getTextGenOutput(model)
        val files = output.outputFiles.await()
        assertTrue(files.isNotEmpty())
        assertEquals(expected, files.size)
    }
}
