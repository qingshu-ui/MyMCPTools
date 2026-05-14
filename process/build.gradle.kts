import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.signing)
    alias(libs.plugins.dokka)
}

group = "io.github.qingshu-ui"
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

val dokkaJar by tasks.registering(Jar::class) {
    from(tasks.dokkaGeneratePublicationHtml)
    dependsOn(tasks.dokkaGeneratePublicationHtml)
    archiveClassifier = "javadoc"
}

tasks {
    withType<AbstractPublishToMaven>().configureEach {
        val signingTasks = withType<Sign>()
        mustRunAfter(signingTasks)
    }

    withType<Jar> {
        metaInf.with(
            copySpec {
                from("${project.rootDir}/LICENSE")
            },
        )
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name = "process"
            description = "A Kotlin Multiplatform process abstraction library providing cross-platform Process and ProcessBuilder expect/actual implementations for JVM, Linux (x64/arm64), and Windows (MinGW)."
            url = "https://github.com/qingshu-ui/MyMCPTools/tree/main/process"
            licenses {
                license {
                    name = "GNU Affero General Public License v3.0"
                    url = "https://github.com/qingshu-ui/MyMCPTools/blob/main/LICENSE"
                }
            }
            developers {
                developer {
                    name = "qingshu-ui"
                    email = "81049953+qingshu-ui@users.noreply.github.com"
                }
            }
            scm {
                connection = "scm:git:git://github.com/qingshu-ui/MyMCPTools.git"
                developerConnection = "scm:git:ssh://github.com:qingshu-ui/MyMCPTools.git"
                url = "https://github.com/qingshu-ui/MyMCPTools/tree/main/process"
            }
        }
        artifact(dokkaJar)
    }
}

signing {
    val secretKeyBase64 = System.getenv("GPG_PRIVATE_KEY") ?: ""

    @OptIn(ExperimentalEncodingApi::class)
    val secretKey = Base64.decode(secretKeyBase64).decodeToString()
    val password = System.getenv("GPG_PRIVATE_PASSWORD") ?: ""
    useInMemoryPgpKeys(
        secretKey,
        password,
    )
    sign(publishing.publications)
}
