import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.modelix.excludeMPSLibraries
import org.modelix.mpsHomeDir
import org.modelix.mpsPlatformVersion

buildscript {
    dependencies {
        classpath("org.modelix.mps:build-tools-lib:1.9.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "org.modelix.mps"

kotlin {
    jvmToolchain(11)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjvm-default=all-compatibility")
    }
    sourceSets {
        main {
            languageSettings {
                apiVersion = KotlinVersion.KOTLIN_1_6.version
            }
        }
    }
}

dependencies {
    fun implementationWithoutBundled(dependencyNotation: Provider<*>) {
        implementation(dependencyNotation, excludeMPSLibraries)
    }

    implementationWithoutBundled(coreLibs.ktor.server.html.builder)
    implementationWithoutBundled(coreLibs.ktor.server.netty)
    implementationWithoutBundled(coreLibs.ktor.server.cors)
    implementationWithoutBundled(coreLibs.ktor.server.status.pages)
    implementationWithoutBundled(coreLibs.kotlin.logging)
    implementationWithoutBundled(libs.modelix.mpsApi)
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    localPath = mpsHomeDir.map { it.asFile.absolutePath }
    instrumentCode = false
    plugins = listOf(
        "Git4Idea",
        "jetbrains.mps.vcs",
    )
}

tasks {
    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("243.*")
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        systemProperty("idea.platform.prefix", "Idea")
        autoReloadPlugins.set(true)
    }

    val mpsPluginDir = project.findProperty("mps$mpsPlatformVersion.plugins.dir")?.toString()?.let { file(it) }
    if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
        register<Sync>("installMpsPlugin") {
            dependsOn(prepareSandbox)
            from(layout.buildDirectory.dir("idea-sandbox/plugins/mps-diff-plugin"))
            into(mpsPluginDir.resolve("mps-diff-plugin"))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.modelix.mps"
            artifactId = "diff-plugin"
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}
