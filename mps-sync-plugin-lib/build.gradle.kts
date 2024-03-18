plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

repositories {
    maven { url = uri("https://www.jetbrains.com/intellij-repository/releases") }
}

kotlin {
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_6)
    }
}

// use the given MPS version, or 2022.2 (last version with JAVA 11) as default
val mpsVersion = project.findProperty("mps.version")?.toString().takeIf { !it.isNullOrBlank() } ?: "2020.3.6"

val mpsZip by configurations.creating

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.kotlin.logging.microutils)

    implementation(libs.modelix.model.api)
    implementation(libs.modelix.model.client)
    implementation(libs.modelix.mps.model.adapters)

    // extracting jars from zipped products
    mpsZip("com.jetbrains:mps:$mpsVersion")
    compileOnly(
        zipTree({ mpsZip.singleFile }).matching {
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
