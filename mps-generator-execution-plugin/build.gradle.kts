import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

buildscript {
    dependencies {
        classpath("org.modelix.mps:build-tools-lib:1.7.4")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij") version "1.17.4"
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
    sourceSets {
        main {
            languageSettings {
                apiVersion = KotlinVersion.KOTLIN_1_4.version
            }
        }
    }
}

dependencies {
    fun ModuleDependency.excludedBundledLibraries() {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-jdk8")
    }
    fun implementationWithoutBundled(dependencyNotation: Provider<*>) {
        implementation(dependencyNotation) {
            excludedBundledLibraries()
        }
    }

    implementationWithoutBundled(coreLibs.ktor.server.html.builder)
    implementationWithoutBundled(coreLibs.ktor.server.netty)
    implementationWithoutBundled(coreLibs.ktor.server.cors)
    implementationWithoutBundled(coreLibs.ktor.server.status.pages)
    implementationWithoutBundled(coreLibs.kotlin.logging)
    implementationWithoutBundled(coreLibs.kotlin.coroutines.swing)

    compileOnly(mpsHome.map { it.files("languages/languageDesign/jetbrains.mps.lang.core.jar") })

    testImplementation(coreLibs.kotlin.coroutines.test)
    testImplementation(coreLibs.ktor.server.test.host)
    testImplementation(coreLibs.ktor.client.cio)
    testImplementation(libs.zt.zip)
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    localPath = mpsHome.map { it.asFile.absolutePath }
    instrumentCode = false
    plugins = listOf(
        "Git4Idea",
        "jetbrains.mps.vcs",
    )
}

tasks {
    patchPluginXml {
        sinceBuild.set("211") // 203 not supported, because VersionFixer was replaced by ModuleDependencyVersions in 211
        untilBuild.set("232.10072.781")
    }

    test {
        // tests currently fail for these versions
        enabled = !setOf(
            211, // jetbrains.mps.vcs plugin cannot be loaded
            212, // timeout because of some deadlock
            213, // timeout because of some deadlock
            222, // timeout because of some deadlock
        ).contains(mpsPlatformVersion)
    }

    buildSearchableOptions {
        enabled = false
    }

    runIde {
        systemProperty("idea.platform.prefix", "Idea")
        autoReloadPlugins.set(true)
    }

    val shortPlatformVersion = mpsVersion.replace(Regex("""20(\d\d)\.(\d+).*"""), "$1$2")
    val mpsPluginDir = project.findProperty("mps$shortPlatformVersion.plugins.dir")?.toString()?.let { file(it) }
    if (mpsPluginDir != null && mpsPluginDir.isDirectory) {
        create<Sync>("installMpsPlugin") {
            dependsOn(prepareSandbox)
            from(buildDir.resolve("idea-sandbox/plugins/mps-generator-execution-plugin"))
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

fun Provider<Directory>.dir(name: String): Provider<Directory> = map { it.dir(name) }
fun Provider<Directory>.file(name: String): Provider<RegularFile> = map { it.file(name) }
fun Provider<Directory>.dir(name: Provider<out CharSequence>): Provider<Directory> = flatMap { it.dir(name) }
