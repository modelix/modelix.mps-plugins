import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.modelix.gradle.mpsplatform.excludeMPSLibraries
import org.modelix.gradle.mpsplatform.mpsHomeDir
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

    implementation(coreLibs.ktor.server.html.builder)
    implementation(coreLibs.ktor.server.netty)
    implementation(coreLibs.ktor.server.cors)
    implementation(coreLibs.ktor.server.status.pages)
    implementation(coreLibs.kotlin.logging)
    implementation(libs.modelix.mpsApi)

    compileOnly(mpsHomeDir.map { it.files("languages/languageDesign/jetbrains.mps.lang.core.jar") })
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // There is no MPS 2025.1 image for the integration tests (mps-generator-execution-plugin-test) yet.
            untilBuild = "243.*"
        }
    }
}

tasks {
    runIde {
        systemProperty("idea.platform.prefix", "Idea")
    }
}

publishMpsPlugin("generator-execution-plugin")
