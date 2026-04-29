plugins {
    kotlin("jvm")
}

group = "io.github.qingshu-ui"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(projects.mcpToolAnnotations)
    implementation(libs.mcp.server)

    testImplementation(libs.kotlin.test)
}
