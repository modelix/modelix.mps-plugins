plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
}

val mpsVersion = project.findProperty("mps.version")?.toString().takeIf { !it.isNullOrBlank() } ?: "2020.3.6"
val mpsHome = rootProject.layout.buildDirectory.dir("mps-$mpsVersion")

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlin.logging.microutils)
    implementation(libs.jackson.dataformat.xml)

    implementation(libs.modelix.model.api)
    implementation(libs.modelix.model.api.gen.runtime)
    implementation(libs.modelix.model.client)
    implementation(libs.modelix.mps.model.adapters)

    compileOnly(
        fileTree(mpsHome).matching {
            include("lib/**/*.jar")
        },
    )
}

group = "org.modelix.mps"
description = "Generic helper library to sync model-server content with MPS"

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "sync-plugin-lib"
            from(components["kotlin"])
        }
    }
}
