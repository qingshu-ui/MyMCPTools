@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        val isCI = System.getenv("GITHUB_ACTIONS") == "true"
        if (!isCI) {
            maven("https://maven.aliyun.com/repository/public/") {
                content {
                    excludeGroup("io.ktor")
                    excludeGroup("io.insert-koin")
                    excludeGroup("de.jonasbroeckmann.kzip")
                    excludeGroup("io.modelcontextprotocol")
                    excludeModuleByRegex("org.jetbrains.kotlinx", "^atomicfu.*$")
                    excludeModuleByRegex("org.jetbrains.kotlinx", "^kotlinx-io.*$")
                    excludeGroupAndSubgroups("com.github.ajalt")
                }
            }
        }
        mavenCentral()
    }
}

pluginManagement {
    repositories {
        val isCI = System.getenv("GITHUB_ACTIONS") == "true"
        if (!isCI) {
            maven("https://maven.aliyun.com/repository/public/")
            maven("https://maven.aliyun.com/repository/gradle-plugin/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "MyMCPTools"
include("mcp-audio-tools")
include("process")
