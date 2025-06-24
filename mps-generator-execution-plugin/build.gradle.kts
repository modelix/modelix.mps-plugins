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
                apiVersion = KotlinVersion.KOTLIN_1_8.version
            }
        }
    }
}

dependencies {
    fun implementationWithoutBundled(dependencyNotation: Provider<*>) {
        implementation(dependencyNotation, excludeMPSLibraries)
    }

    implementation(coreLibs.ktor.server.html.builder)
    implementation(coreLibs.ktor.server.netty)
    implementation(coreLibs.ktor.server.cors)
    implementation(coreLibs.ktor.server.status.pages)
    implementation(coreLibs.kotlin.logging)
    implementation(libs.modelix.mpsApi)

    compileOnly(mpsHomeDir.map { it.files("languages/languageDesign/jetbrains.mps.lang.core.jar") })
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    localPath = mpsHomeDir.map { it.asFile.absolutePath }
    instrumentCode = false
}

tasks {
    patchPluginXml {
        sinceBuild.set("203")
        untilBuild.set("243.*")
    }

    test {
        // tests currently fail for these versions
//        enabled = !setOf(
//            211, // jetbrains.mps.vcs plugin cannot be loaded
//            212, // timeout because of some deadlock
//            213, // timeout because of some deadlock
//            222, // timeout because of some deadlock
//        ).contains(mpsPlatformVersion)

        // incompatibility of ktor 3 with the bundled coroutines version
        enabled = true

        jvmArgs("-Dintellij.platform.load.app.info.from.resources=true")
        val arch = System.getProperty("os.arch")
        val jnaDir = mpsHomeDir.get().asFile.resolve("lib/jna/$arch")
        if (jnaDir.exists()) {
            jvmArgs("-Djna.boot.library.path=${jnaDir.absolutePath}")
            jvmArgs("-Djna.noclasspath=true")
            jvmArgs("-Djna.nosys=true")
        }
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
            from(layout.buildDirectory.dir("idea-sandbox/plugins/mps-generator-execution-plugin"))
            into(mpsPluginDir.resolve("mps-generator-execution-plugin"))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.modelix.mps"
            artifactId = "generator-execution-plugin"
            artifact(tasks.buildPlugin) {
                extension = "zip"
            }
        }
    }
}
