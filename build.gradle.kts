import com.diffplug.gradle.spotless.BaseKotlinExtension
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType

plugins {
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.nexusPublish)
    alias(libs.plugins.dokka) apply false
}

nexusPublishing {
    repositories {
        sonatype {
            packageGroup = "io.github.qingshu-ui"
            nexusUrl = uri("https://ossrh-staging-api.central.sonatype.com/service/local/")
            snapshotRepositoryUrl =
                uri(
                    "https://ossrh-staging-api.central.sonatype.com/content/repositories/snapshots/",
                )
            username = System.getenv("SONATYPE_USERNAME")
            password = System.getenv("SONATYPE_PASSWORD")
        }
    }
}

tasks.wrapper {
    val mirror = "https://mirrors.aliyun.com/macports/distfiles/gradle/gradle-9.4.0-all.zip"
    val official = "https://services.gradle.org/distributions/gradle-9.4.0-bin.zip"
    val isCI = System.getenv("GITHUB_ACTIONS") == "true"
    gradleVersion = "9.4.0"
    distributionType = if (isCI) DistributionType.BIN else DistributionType.ALL
    distributionUrl = if (isCI) official else mirror
}

val spotlessPlugin = libs.plugins.spotless.get().pluginId
allprojects {
    apply(plugin = spotlessPlugin)

    configure<SpotlessExtension> {
        kotlin {
            target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
            ktlint().currentProjectStyle {
                val customRules = listOf(
                    "io.nlopez.compose.rules:ktlint:0.5.6",
                )
                val composeProject = emptyList<String>()
                if (this@allprojects.name in composeProject) {
                    logger.info("project: ${this@allprojects.name}, add compose rule.")
                    customRuleSets(customRules)
                }
            }
        }

        kotlinGradle {
            target("*.gradle.kts")
            ktlint().currentProjectStyle()
        }
    }
}

fun BaseKotlinExtension.KtlintConfig.currentProjectStyle(block: BaseKotlinExtension.KtlintConfig.() -> Unit = {}) {
    val overrideEditConfig = mapOf(
        "ktlint_standard_package-name" to "disabled",
        "ktlint_standard_function-naming" to "disabled",
        "ktlint_standard_no-unused-imports" to "enabled",
        "ktlint_standard_multiline-if-else" to "disabled",
    )

    editorConfigOverride(overrideEditConfig)
    block()
}
