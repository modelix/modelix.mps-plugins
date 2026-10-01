import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.modelix.gradle.mpsplatform.excludeMPSLibraries
import org.modelix.gradle.mpsplatform.publishMpsPlugin

buildscript {
    dependencies {
        classpath("org.modelix.mps:build-tools-lib:1.9.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.modelix.mps.plugin")
}

group = "org.modelix.mps"

kotlin {
    jvmToolchain(17)
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

    implementationWithoutBundled(coreLibs.ktor.server.html.builder)
    implementationWithoutBundled(coreLibs.ktor.server.netty)
    implementationWithoutBundled(coreLibs.ktor.server.cors)
    implementationWithoutBundled(coreLibs.ktor.server.status.pages)
    implementationWithoutBundled(coreLibs.kotlin.logging)
    implementationWithoutBundled(libs.modelix.mpsApi)

    intellijPlatform {
        bundledPlugin("Git4Idea")
        bundledPlugin("jetbrains.mps.vcs")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // There is no MPS 2025.1 image for the integration tests (mps-diff-plugin-test) yet.
            untilBuild = "243.*"
        }
    }
}

tasks {
    runIde {
        systemProperty("idea.platform.prefix", "Idea")
    }
}

publishMpsPlugin("diff-plugin")
