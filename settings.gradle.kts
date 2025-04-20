pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
        mavenCentral()
    }
    dependencyResolutionManagement {
        repositories {
            mavenLocal()
            gradlePluginPortal()
            maven { url = uri("https://artifacts.itemis.cloud/repository/maven-mps/") }
            mavenCentral()
        }
        versionCatalogs {
            create("coreLibs") {
                val modelixCoreVersion = file("gradle/libs.versions.toml").readLines()
                    .first { it.startsWith("modelixCore = ") }
                    .substringAfter('"')
                    .substringBefore('"')
                from("org.modelix:core-version-catalog:$modelixCoreVersion")
            }
        }
    }
}

rootProject.name = "modelix.mps-plugins"

include("mps-diff-plugin")
include("mps-diff-plugin-test")
include("mps-generator-execution-plugin")
include("mps-generator-execution-plugin-test")
