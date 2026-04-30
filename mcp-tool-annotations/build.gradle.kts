plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "io.github.qingshu-ui"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
    jvm()
    linuxX64()
    linuxArm64()
    mingwX64()

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
