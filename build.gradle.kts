import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin
import org.modelix.copyMps

buildscript {
    dependencies {
        classpath(coreLibs.semver)
    }
}

plugins {
    `maven-publish`
    `version-catalog`
    alias(coreLibs.plugins.kotlin.multiplatform) apply false
    alias(coreLibs.plugins.kotlin.serialization) apply false
    alias(coreLibs.plugins.gitVersion)
    alias(coreLibs.plugins.node) apply false
}

group = "org.modelix.mps"
version = computeVersion()
println("Version: $version")

fun computeVersion(): Any {
    val versionFile = file("version.txt")
    val gitVersion: groovy.lang.Closure<String> by extra
    return if (versionFile.exists()) {
        versionFile.readText().trim()
    } else {
        gitVersion()
            // Avoid duplicated "-SNAPSHOT" ending
            .let { if (it.endsWith("-SNAPSHOT")) it else "$it-SNAPSHOT" }
            // Normalize the version so that is always a valid NPM version.
            .let { if (it.matches("""\d+\.\d+.\d+-.*""".toRegex())) it else "0.0.1-$it" }
            .also { versionFile.writeText(it) }
    }
}

val parentProject = project

subprojects {
    val subproject = this
    apply(plugin = "maven-publish")

    version = rootProject.version
    group = rootProject.group

    val kotlinApiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_6
    subproject.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs += listOf("-Xjvm-default=all-compatibility")
            if (!name.lowercase().contains("test")) {
                apiVersion = kotlinApiVersion.version
            }
        }
    }
    subproject.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "11"
            freeCompilerArgs += listOf("-Xjvm-default=all-compatibility")
            if (!name.lowercase().contains("test")) {
                apiVersion = kotlinApiVersion.version
            }
        }
    }

    subproject.plugins.withType<JavaPlugin> {
        subproject.extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }

    subproject.plugins.withType<KotlinPlatformJvmPlugin> {
        subproject.extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }

    subproject.plugins.withType<KotlinMultiplatformPluginWrapper> {
        subproject.extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.all {
                if (!name.lowercase().contains("test")) {
                    languageSettings {
                        apiVersion = kotlinApiVersion.version
                    }
                }
            }
        }
    }
}

allprojects {
    repositories {
        mavenLocal()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven") }
    }

    publishing {
        repositories {
            maven {
                name = "itemis"
                url = if (version.toString().contains("SNAPSHOT")) {
                    uri("https://artifacts.itemis.cloud/repository/maven-mps-snapshots/")
                } else {
                    uri("https://artifacts.itemis.cloud/repository/maven-mps-releases/")
                }
                credentials {
                    username = project.findProperty("artifacts.itemis.cloud.user").toString()
                    password = project.findProperty("artifacts.itemis.cloud.pw").toString()
                }
            }
        }
    }

    // Set maven metadata for all known publishing tasks. The exact tasks and names are only known after evaluation.
    afterEvaluate {
        tasks.withType<AbstractPublishToMaven>() {
            this.publication?.apply {
                setMetadata()
            }
        }
    }
}

fun MavenPublication.setMetadata() {
    pom {
        url.set("https://github.com/modelix/modelix.mps-plugins")
        scm {
            connection.set("scm:git:https://github.com/modelix/modelix.mps-plugins.git")
            url.set("https://github.com/modelix/modelix.mps-plugins")
        }
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
    }
}

copyMps()
