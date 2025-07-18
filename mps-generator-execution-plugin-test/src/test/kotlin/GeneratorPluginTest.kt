import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

@RunWith(Parameterized::class)
class GeneratorPluginTest(val mpsVersion: String) {

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun mpsVersions() = listOf(
            "2024.3",
            "2024.1",
            "2023.3",
            "2023.2",
            "2022.3",
            "2022.2",
            "2021.3",
            "2021.2",
            "2021.1",
            "2020.3",
        )
    }

    private fun runWithMPS(body: suspend (port: Int) -> Unit) = runTest(timeout = 3.minutes) {
        println("MPS version: $mpsVersion")

        val mps: GenericContainer<*> = GenericContainer("docker.io/modelix/mps-vnc-baseimage:0.9.4-mps$mpsVersion")
            .withExposedPorts(33335)
            .withCopy(
                "../mps-generator-execution-plugin/build/idea-sandbox/plugins/mps-generator-execution-plugin",
                "/mps/plugins/mps-generator-execution-plugin",
            )
            .withCopy(
                "testdata/SimpleProject",
                "/mps-projects/SimpleProject",
            )
            .waitingFor(Wait.forListeningPort().withStartupTimeout(3.minutes.toJavaDuration()))
            .withLogConsumer {
                println(it.utf8StringWithoutLineEnding)
            }

        mps.start()
        try {
            body(mps.firstMappedPort)
        } finally {
            mps.stop()
        }
    }

    @Test
    fun `single raw file`() = runWithMPS { port ->
        val client = HttpClient(CIO)
        val urlString = "http://localhost:$port/modules/6517ba0d-f632-49c5-a166-401587c2c3ca/models/r%3A630db018-71e0-498d-8b21-6ec252b3533a/generatorOutput/files/Class1.java/view"
        val actual = client.get(urlString).bodyAsText()
        val expected = """
            package Solution1.model1;

            /*Generated by MPS */


            public class Class1 {
              public void method1() {
              }
            }

        """.trimIndent()
        assertEquals(expected, actual)
    }
}

private fun GenericContainer<*>.withCopy(from: String, to: String): GenericContainer<*> {
    require(File(from).exists()) { "Doesn't exist: $from" }
    return withCopyFileToContainer(MountableFile.forHostPath(from), to)
}
