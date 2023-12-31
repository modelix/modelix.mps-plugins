import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinMultiplatformPluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformJvmPlugin

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
}

val mpsVersion = project.findProperty("mps.version")?.toString()?.takeIf { it.isNotEmpty() }
    ?: "2021.1.4".also { ext["mps.version"] = it }
println("Building for MPS version $mpsVersion")
val mpsJavaVersion = if (mpsVersion >= "2022.2") 17 else 11
ext["mps.java.version"] = mpsJavaVersion

// Extract MPS during configuration phase, because using it in intellij.localPath requires it to already exist.
val mpsHome = project.layout.buildDirectory.dir("mps-$mpsVersion")
val mpsZip by configurations.creating
dependencies { mpsZip("com.jetbrains:mps:$mpsVersion") }
mpsHome.get().asFile.let { baseDir ->
    if (baseDir.exists()) return@let // content of MPS zip is not expected to change

    println("Extracting MPS ...")
    sync {
        from(zipTree({ mpsZip.singleFile }))
        into(mpsHome)
    }

    // The build number of a local IDE is expected to contain a product code, otherwise an exception is thrown.
    val buildTxt = mpsHome.get().asFile.resolve("build.txt")
    val buildNumber = buildTxt.readText()
    val prefix = "MPS-"
    if (!buildNumber.startsWith(prefix)) {
        buildTxt.writeText("$prefix$buildNumber")
    }

    println("Extracting MPS done.")
}
