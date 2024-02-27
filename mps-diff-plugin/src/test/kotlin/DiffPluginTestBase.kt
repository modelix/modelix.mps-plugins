import com.intellij.openapi.application.ApplicationManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import io.ktor.client.HttpClient
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.modelix.ui.diff.DiffServer
import org.zeroturnaround.zip.ZipUtil
import java.io.File
import java.nio.file.Path

@Suppress("removal")
abstract class DiffPluginTestBase() : HeavyPlatformTestCase() {
    protected lateinit var projectDir: Path
    protected lateinit var httpClient: HttpClient

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

    protected fun runTestWithDiffServer(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val diffServer = ApplicationManager.getApplication().getService(DiffServer::class.java)
        diffServer.registerProject(project)
        application {
            diffServer.initKtorServer(this)
        }
        block()
    }

    override fun runInDispatchThread(): Boolean = false

    override fun isCreateDirectoryBasedProject(): Boolean = true

    override fun getProjectDirOrFile(isDirectoryBasedProject: Boolean): Path {
        val projectDir = super.getProjectDirOrFile(isDirectoryBasedProject)
        println("projectDir: " + projectDir)
        ZipUtil.unpack(File("diff-test-project.zip"), projectDir.toFile())
        this.projectDir = projectDir
        return projectDir
    }
}
