buildscript {
    dependencies {
        classpath("org.modelix.mps:build-tools-lib:1.9.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation(coreLibs.kotlin.coroutines.test)
    implementation(coreLibs.ktor.client.cio)
    implementation(coreLibs.testcontainers)
}

tasks {
    test {
        dependsOn(":mps-generator-execution-plugin:prepareSandbox")
    }
}
