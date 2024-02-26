import com.intellij.ide.plugins.PluginManager
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

class DiffPluginTest : DiffPluginTestBase() {

    fun testMpsVcsPluginLoaded() {
        val pluginId = "jetbrains.mps.vcs"
        val loadedPluginIds = PluginManager.getPlugins().map { it.pluginId.idString }.toSet()
        val message = "Plugin $pluginId not found. Found only:\n" + loadedPluginIds.joinToString("\n") { "  $it" } + "\n"
        assertTrue(message, loadedPluginIds.contains(pluginId))
    }

    fun testDiffImages() = runTestWithDiffServer {
        PluginManager.getPlugins().map { it.name }.forEach(::println)

        val baseUrl = "http://localhost"
        val diffPageHtml = client.get("$baseUrl/54ff8ce1442ac24e065d0ba99ae96b752a4427cf/38fd1c4668b8bb600a4193def54ae4281045c790/").bodyAsText()
        println(diffPageHtml)
        val imageNames = Regex("""src="([^\"]+)\.png"""").findAll(diffPageHtml).map { it.groupValues.first() }.toList()
        println(imageNames)
        assertEquals(3, imageNames.size)
    }
}
