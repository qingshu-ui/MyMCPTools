@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
dependencyResolutionManagement {
    repositories {
        System.getenv("GITHUB_ACTIONS")?.let {
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
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

pluginManagement {
    repositories {
        System.getenv("GITHUB_ACTIONS")?.let {
            maven("https://maven.aliyun.com/repository/public/")
            maven("https://maven.aliyun.com/repository/gradle-plugin/")
        }
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "MyMCPTools"
include("mcp-audio-tools")
include("process")
