import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
}

group = "io.github.qingshu-ui"
version = "1.0.0"

dependencies {
    add("kspCommonMainMetadata", projects.mcpToolKsp)
}

kotlin {
    jvmToolchain(21)
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    listOf(
        mingwX64(),
        linuxX64(),
        linuxArm64(),
    ).forEach { target ->
        target.apply {
            binaries {
                executable {
                    entryPoint = "io.github.qingshu.mcpaudiotools.main"
                    baseName += "-$version"
                    if (buildType == NativeBuildType.RELEASE) {
                        binaryOption("smallBinary", "true")
                    }
                }
            }
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }

    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/ksp/metadata/commonMain/kotlin"))
            dependencies {
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.serializationJson)
                implementation(libs.mcp.server)
                implementation(projects.mcpToolAnnotations)
                implementation(projects.process)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(tasks.named("kspCommonMainKotlinMetadata"))
}

tasks.withType<ProcessResources>().configureEach {
    dependsOn(tasks.named("kspCommonMainKotlinMetadata"))
}

tasks.register<Jar>("fatJar") {
    group = "build"
    description = "Creates a fat/uber JAR with all runtime dependencies bundled"
    archiveClassifier.set("all")

    from(kotlin.targets["jvm"].compilations["main"].output)
    dependsOn(configurations["jvmRuntimeClasspath"])

    from({
        configurations["jvmRuntimeClasspath"]
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Main-Class" to "io.github.qingshu.mcpaudiotools.MainKt",
        )
    }
}

tasks.withType<KotlinNativeLink>().configureEach {
    val taskName = name.lowercase()
    enabled = when {
        taskName.contains("linux") -> HostManager.hostIsLinux
        taskName.contains("mingw") -> HostManager.hostIsMingw
        taskName.contains("macos") -> HostManager.hostIsMac
        else -> true
    }
}
