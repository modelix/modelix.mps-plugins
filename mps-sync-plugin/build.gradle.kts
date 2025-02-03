import org.modelix.excludeMPSLibraries
import org.modelix.mpsHomeDir

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "org.modelix.mps"

dependencies {
    fun implementationWithoutBundled(dependencyNotation: Provider<*>) {
        implementation(dependencyNotation, excludeMPSLibraries)
    }

    implementationWithoutBundled(libs.logback.classic)
    implementationWithoutBundled(libs.kotlin.logging.microutils)

    implementation(libs.modelix.model.api, excludeMPSLibraries)
    implementationWithoutBundled(libs.modelix.model.client)
    implementationWithoutBundled(libs.modelix.modelql.core)
    implementationWithoutBundled(libs.modelix.modelql.untyped)

    implementation(project(":mps-sync-plugin-lib"), excludeMPSLibraries)

    compileOnly(
        fileTree(mpsHomeDir).matching {
            include("lib/**/*.jar")
        },
    )
}

// Configure Gradle IntelliJ Plugin (https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html)
intellij {
    localPath = mpsHomeDir.map { it.asFile.absolutePath }
    instrumentCode = false
    plugins = listOf("jetbrains.mps.ide.make")
}

kotlin {
    jvmToolchain(11)
}

tasks {
    patchPluginXml {
        sinceBuild.set("203")
        untilBuild.set("232.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        systemProperty("idea.platform.prefix", "Idea")
        autoReloadPlugins.set(true)
    }

    val mpsPluginDir = project.findProperty("mps.plugins.dir")?.toString()?.let { file(it) }
    if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
        create<Sync>("installMpsPlugin") {
            dependsOn(prepareSandbox)
            from(buildDir.resolve("idea-sandbox/plugins/mps-sync-plugin"))
            into(mpsPluginDir.resolve("mps-sync-plugin"))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "sync-plugin"
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}
