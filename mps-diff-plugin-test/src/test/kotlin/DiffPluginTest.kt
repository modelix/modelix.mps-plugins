package org.modelix.mps.generator.web

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
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
class DiffPluginTest(val mpsVersion: String) {

    companion object {
        val timeout = 5.minutes

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun mpsVersions() = listOf(
            "2024.3",
            "2024.1",
        )
    }

    private fun runWithMPS(body: suspend (port: Int) -> Unit) = runTest(timeout = timeout) {
        println("MPS version: $mpsVersion")

        val mps: GenericContainer<*> = GenericContainer("docker.io/modelix/mps-vnc-baseimage:0.9.4-mps$mpsVersion")
            .withExposedPorts(33334)
            .withCopy(
                sandboxPluginDir("mps-diff-plugin").path,
                "/mps/plugins/mps-diff-plugin",
            )
            .withCopy(
                "build/diff-test-project",
                "/mps-projects/diff-test-project",
            )
//            .withCreateContainerCmdModifier {
//                it.withPlatform("linux/amd64")
//            }
            .waitingFor(Wait.forListeningPort().withStartupTimeout(timeout.toJavaDuration()))
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
    fun `diff images page`() = runWithMPS { port ->
        val client = HttpClient(CIO)
        val baseUrl = "http://localhost:$port"
        val diffUrl = "$baseUrl/54ff8ce1442ac24e065d0ba99ae96b752a4427cf/38fd1c4668b8bb600a4193def54ae4281045c790/"
        val diffPageHtml = client.get(diffUrl).bodyAsText()
        println(diffPageHtml)
        val folder = File("build/diff-images/$mpsVersion/")
        folder.mkdirs()
        folder.resolve("index.html").writeText(diffPageHtml)

        val imageNames = Regex("""src="([^\"]+\.png)"""").findAll(diffPageHtml).map { it.groupValues[1] }.toList()
        println(imageNames)
        assertEquals(3, imageNames.size)

        for (imageName in imageNames) {
            val data = client.get("$diffUrl/$imageName").bodyAsBytes()
            folder.resolve(imageName).writeBytes(data)
        }
    }
}

private fun GenericContainer<*>.withCopy(from: String, to: String): GenericContainer<*> {
    require(File(from).exists()) { "Doesn't exist: $from" }
    return withCopyFileToContainer(MountableFile.forHostPath(from), to)
}

/**
 * The plugin folder in the sandbox of the IntelliJ Platform Gradle Plugin, which contains the version of the platform
 * (e.g. build/idea-sandbox/MPS-2024.1/plugins/mps-diff-plugin).
 */
private fun sandboxPluginDir(pluginName: String): File {
    val sandbox = File("../$pluginName/build/idea-sandbox")
    return sandbox.walk()
        .filter { it.isDirectory && it.name == pluginName && it.parentFile.name == "plugins" }
        .maxByOrNull { it.lastModified() }
        ?: error("Plugin $pluginName not found in $sandbox")
}
