buildscript {
    dependencies {
        classpath("org.modelix.mps:build-tools-lib:2.0.1")
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
