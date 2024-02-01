import com.jetbrains.plugin.structure.base.utils.contentBuilder.buildZipFile
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipInputStream

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.16.1"
}

group = "org.modelix.mps"
val mpsVersion = project.findProperty("mps.version").toString()
val mpsHome = rootProject.layout.buildDirectory.dir("mps-$mpsVersion")

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    api(libs.modelix.model.api)

    implementation(libs.modelix.mps.model.adapters)
    implementation(libs.modelix.model.client)
    implementation(libs.modelix.model.datastructure)
    implementation(coreLibs.ktor.client.core)
    implementation(coreLibs.ktor.serialization.json)
    implementation(coreLibs.trove4j)
    implementation(coreLibs.kotlin.datetime)

    implementation(coreLibs.kotlin.reflect)

    // There is a usage of MakeActionParameters in ProjectMakeRunner which we might want to delete
    compileOnly(mpsHome.map { it.files("plugins/mps-make/languages/jetbrains.mps.ide.make.jar") })

    testImplementation(coreLibs.kotlin.coroutines.test)
    testImplementation(coreLibs.ktor.server.test.host)
    testImplementation(libs.modelix.model.server)
    testImplementation(libs.modelix.authorization)
    testImplementation(coreLibs.kotlin.reflect)
//    implementation(libs.ktor.server.core)
//    implementation(libs.ktor.server.cors)
//    implementation(libs.ktor.server.netty)
//    implementation(libs.ktor.server.html.builder)
//    implementation(libs.ktor.server.auth)
//    implementation(libs.ktor.server.auth.jwt)
//    implementation(libs.ktor.server.status.pages)
//    implementation(libs.ktor.server.forwarded.header)
    testImplementation(coreLibs.ktor.server.websockets)
    testImplementation(coreLibs.ktor.server.content.negotiation)
//    implementation(libs.ktor.serialization.json)
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    localPath = mpsHome.map { it.asFile.absolutePath }
    instrumentCode = false
    plugins = listOf("jetbrains.mps.ide.make")
}

tasks {
    patchPluginXml {
        sinceBuild.set("211") // 203 not supported, because VersionFixer was replaced by ModuleDependencyVersions in 211
        untilBuild.set("232.10072.781")
    }

    register("mpsCompatibility") { dependsOn("build") }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        autoReloadPlugins.set(true)
    }

    val mpsPluginDir = project.findProperty("mps232.plugins.dir")?.toString()?.let { file(it) }
    if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
        create<Sync>("installMpsPlugin") {
            dependsOn(prepareSandbox)
            from(buildDir.resolve("idea-sandbox/plugins/mps-legacy-sync-plugin"))
            into(mpsPluginDir.resolve("mps-legacy-sync-plugin"))
        }
    }

    withType<PrepareSandboxTask> {
        intoChild(pluginName.map { "$it/languages" })
            .from(project.layout.projectDirectory.dir("repositoryconcepts"))
    }

    val checkBinaryCompatibility by registering {
        group = "verification"
        doLast {
            val ignoredFiles = setOf("META-INF/MANIFEST.MF")
            fun loadEntries(fileName: String) = project.layout.buildDirectory.file("binary-compatibility/$fileName").get().asFile.inputStream().use {
                val zip = ZipInputStream(it)
                val entries = generateSequence { zip.nextEntry }
                entries.associate { it.name to "size:${it.size},crc:${it.crc}" }
            } - ignoredFiles
            val entriesA = loadEntries("a.jar")
            val entriesB = loadEntries("b.jar")
            val mismatches = (entriesA.keys + entriesB.keys).map { it to (entriesA[it] to entriesB[it]) }.filter { it.second.first != it.second.second }
            check(mismatches.isEmpty()) {
                "The following files have a different content:\n" + mismatches.joinToString("\n") { "  ${it.first}: ${it.second.first} != ${it.second.second}" }
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.modelix.mps"
            artifactId = "legacy-sync-plugin"
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}
