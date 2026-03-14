plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

group = "io.github.qingshu_ui"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
    jvm()
    linuxX64()
    linuxArm64()
    mingwX64()

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.kotlinx.io)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
