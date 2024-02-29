import com.intellij.openapi.application.ApplicationManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.mps.generator.web.GeneratorServer
import java.nio.file.Path

abstract class GeneratorPluginTestBase() : HeavyPlatformTestCase() {
    protected lateinit var projectDir: Path
    protected lateinit var httpClient: HttpClient
    protected lateinit var generatorServer: GeneratorServer

    override fun tearDown() {
        EdtTestUtil.runInEdtAndGet<_, Throwable> {
            try {
                super.tearDown()
            } catch (ex: AlreadyDisposedException) {
                Exception("Ignoring exception", ex).printStackTrace()
            }
        }
    }

    override fun setUp() {
        EdtTestUtil.runInEdtAndGet<_, Throwable> {
            super.setUp()
        }
    }

    protected fun runTestWithGeneratorServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val generatorServer = ApplicationManager.getApplication().getService(GeneratorServer::class.java)
        this@GeneratorPluginTestBase.generatorServer = generatorServer
        generatorServer.registerProject(project)
        application {
            generatorServer.initKtorServer(this)
        }
        block()
    }

    override fun runInDispatchThread(): Boolean = false

    override fun isCreateDirectoryBasedProject(): Boolean = true

    override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path {
        val projectDir = super.getProjectDirOrFile(isDirectoryBasedProject)
        println("projectDir: " + projectDir)
        this.projectDir = projectDir
        return projectDir
    }
}
