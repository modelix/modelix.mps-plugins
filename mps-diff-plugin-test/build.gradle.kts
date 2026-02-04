import org.zeroturnaround.zip.ZipUtil

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
    val unzipTestProject by registering {
        ZipUtil.unpack(layout.projectDirectory.file("diff-test-project.zip").asFile, layout.buildDirectory.dir("diff-test-project").get().asFile)
    }
    test {
        dependsOn(":mps-diff-plugin:prepareSandbox")
        dependsOn(unzipTestProject)
    }
}
