import com.jetbrains.plugin.structure.base.utils.contentBuilder.buildZipFile
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.modelix.buildtools.ModuleId
import org.modelix.buildtools.ModuleIdAndName
import org.modelix.buildtools.StubsSolutionGenerator
import java.util.zip.ZipInputStream

buildscript {
    dependencies {
        classpath("org.modelix.mps:build-tools-lib:1.4.1")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "org.modelix.mps"
val mpsVersion = project.findProperty("mps.version").toString()
val mpsPlatformVersion = project.findProperty("mps.platform.version").toString().toInt()
val mpsHome = rootProject.layout.buildDirectory.dir("mps-$mpsVersion")

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
    withSourcesJar()
}

tasks.compileJava {
    options.release = 11
}

tasks.compileKotlin {
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf("-Xjvm-default=all-compatibility")
        apiVersion = "1.6"
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
    implementation(coreLibs.ktor.server.resources)

    // There is a usage of MakeActionParameters in ProjectMakeRunner which we might want to delete
    compileOnly(mpsHome.map { it.files("plugins/mps-make/languages/jetbrains.mps.ide.make.jar") })

    testImplementation(coreLibs.kotlin.coroutines.test)
    testImplementation(coreLibs.ktor.server.test.host)
    testImplementation(libs.modelix.model.server)
    testImplementation(libs.modelix.authorization)
    testImplementation(coreLibs.kotlin.reflect)
    testImplementation(coreLibs.ktor.server.websockets)
    testImplementation(coreLibs.ktor.server.content.negotiation)
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    localPath = mpsHome.map { it.asFile.absolutePath }
    instrumentCode = false
    plugins = listOf("jetbrains.mps.ide.make")
}

data class TestPartition(val partitionName: String, val partitionPattern: String)
// Some test need to run in isolation from other tests.
// A test task (Test::class) runs all tests in the same JVM and same MPS instance.
val testClassPartitionsToRunInIsolation = listOf(
    // `ProjectorAutoBindingTest` sets system properties to enable `EModelixExecutionMode.PROJECTOR`.
    // The system properties should not influence the other tests.
    // Also, currently the execution mode is application-specific and not project-specific.
    TestPartition("ProjectorAutoBindingTest", "**/ProjectorAutoBindingTest.class"),
)

// Tests currently fail for these versions because of some deadlock.
// The deadlock does not seem to be caused by our test code.
// Even an unmodified `HeavyPlatformTestCase` hangs.
val enableTests = !setOf(212, 213, 222).contains(mpsPlatformVersion)

tasks {
    patchPluginXml {
        sinceBuild.set("211") // 203 is not supported, because VersionFixer was replaced by ModuleDependencyVersions in 211
        untilBuild.set("232.10072.781")
    }

    test {
        enabled = enableTests
        useJUnit {
            setExcludes(testClassPartitionsToRunInIsolation.map { it.partitionPattern })
        }
    }

    for (testClassToRunInIsolation in testClassPartitionsToRunInIsolation) {
        val testTask = register("test${testClassToRunInIsolation.partitionName}", Test::class) {
            enabled = enableTests
            useJUnit {
                include(testClassToRunInIsolation.partitionPattern)
            }
        }
        check {
            dependsOn(testTask)
        }
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        systemProperty("idea.platform.prefix", "Idea")
        autoReloadPlugins.set(true)
    }

    val mpsPluginDir = project.findProperty("mps232.plugins.dir")?.toString()?.let { file(it) }
    val generatedStubsSolution by registering {
        val pluginFolder = project.layout.buildDirectory.dir("idea-sandbox").dir("plugins").dir(prepareSandbox.flatMap { it.pluginName })
        val libFolder = pluginFolder.dir("lib")
        val languagesFolder = pluginFolder.dir("languages")
        inputs.dir(libFolder)
        doLast {
            val solutionName = "org.modelix.model.sync.stubs"
            val stubsSolutionJar = languagesFolder.get().asFile.resolve("$solutionName.jar")
            val dependencies: List<ModuleIdAndName> = listOf(
                ModuleIdAndName.fromString("8865b7a8-5271-43d3-884c-6fd1d9cfdd34(MPS.OpenAPI)"),
                ModuleIdAndName.fromString("6ed54515-acc8-4d1e-a16c-9fd6cfe951ea(MPS.Core)"),
//                ModuleIdAndName.fromString("3f233e7f-b8a6-46d2-a57f-795d56775243(Annotations)"),
//                ModuleIdAndName.fromString("6354ebe7-c22a-4a0f-ac54-50b52ab9b065(JDK)"),
                ModuleIdAndName.fromString("86441d7a-e194-42da-81a5-2161ec62a379(MPS.Workbench)"),
                ModuleIdAndName.fromString("742f6602-5a2f-4313-aa6e-ae1cd4ffdc61(MPS.Platform)"),
                ModuleIdAndName.fromString("498d89d2-c2e9-11e2-ad49-6cf049e62fe5(MPS.IDEA)"),
            )
            buildZipFile(stubsSolutionJar.toPath()) {
                dir("modules") {
                    dir(solutionName) {
                        file("$solutionName.msd") {
                            val generatedString = StubsSolutionGenerator(
                                ModuleIdAndName(ModuleId("c5e5433e-201f-43e2-ad14-a6cba8c80cd6"), solutionName),
                                libFolder.get().asFile.listFiles().map { "\${module}/../../../../lib/${it.name}" }
                                    .sorted(),
                                dependencies,
                            ).generateString()
                            // newer MPS versions use classes="provided", older MPS version use the ideaPlugin facet
                            // TODO fix the StubsSolutionGenerator
                            generatedString.replace(
                                """<facet type="java"/>""",
                                """<facet classes="provided" type="java"/><facet pluginId="org.modelix.mps.sync.legacy" type="ideaPlugin" />""",
                            )
                        }
                    }
                }
            }
        }
    }
    prepareSandbox {
        finalizedBy(generatedStubsSolution)
    }

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
            val ignoredFiles = setOf(
                "META-INF/MANIFEST.MF",
                "org/modelix/model/mpsplugin/AllowedBinaryIncompatibilityKt.class",
            )
            fun loadEntries(fileName: String) = rootProject.layout.buildDirectory
                .dir("binary-compatibility")
                .dir(project.name)
                .file(fileName)
                .get().asFile.inputStream().use {
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

fun Provider<Directory>.dir(name: String): Provider<Directory> = map { it.dir(name) }
fun Provider<Directory>.file(name: String): Provider<RegularFile> = map { it.file(name) }
fun Provider<Directory>.dir(name: Provider<out CharSequence>): Provider<Directory> = flatMap { it.dir(name) }
