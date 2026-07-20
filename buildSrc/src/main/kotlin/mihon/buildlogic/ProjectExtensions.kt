package mihon.buildlogic

import com.android.build.gradle.BaseExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

val Project.libsCatalog: VersionCatalog get() = the<VersionCatalogsExtension>().named("libs")
val Project.androidxCatalog: VersionCatalog get() = the<VersionCatalogsExtension>().named("androidx")
val Project.composeCatalog: VersionCatalog get() = the<VersionCatalogsExtension>().named("compose")
val Project.kotlinxCatalog: VersionCatalog get() = the<VersionCatalogsExtension>().named("kotlinx")

internal fun Project.configureAndroid() {
    if (!pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
        pluginManager.apply("org.jetbrains.kotlin.android")
    }

    val android = extensions.getByType(BaseExtension::class.java)
    android.apply {
        compileSdkVersion(AndroidConfig.COMPILE_SDK)
        buildToolsVersion(AndroidConfig.BUILD_TOOLS)

        defaultConfig {
            minSdk = AndroidConfig.MIN_SDK
            ndk {
                version = AndroidConfig.NDK
            }
        }

        compileOptions {
            sourceCompatibility = AndroidConfig.JavaVersion
            targetCompatibility = AndroidConfig.JavaVersion
            isCoreLibraryDesugaringEnabled = true
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(AndroidConfig.JvmTarget)
            freeCompilerArgs.addAll(
                "-Xcontext-receivers",
                "-opt-in=kotlin.RequiresOptIn",
            )

            // Treat all Kotlin warnings as errors (disabled by default)
            // Override by setting warningsAsErrors=true in your ~/.gradle/gradle.properties
            val warningsAsErrors = project.findProperty("warningsAsErrors")?.toString()
            allWarningsAsErrors.set(warningsAsErrors.toBoolean())

        }
    }

    dependencies {
        "coreLibraryDesugaring"(libsCatalog.findLibrary("desugar").get().get())
    }
}

internal fun Project.configureCompose() {
    val composeCompilerPluginId = kotlinxCatalog.findPlugin("compose-compiler").get().get().pluginId
    pluginManager.apply(composeCompilerPluginId)

    val android = extensions.getByType(BaseExtension::class.java)
    android.apply {
        buildFeatures.compose = true

        dependencies {
            "implementation"(platform(composeCatalog.findLibrary("bom").get().get()))
        }
    }

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        featureFlags.set(setOf(ComposeFeatureFlag.OptimizeNonSkippingGroups))

        val enableMetrics = project.providers.gradleProperty("enableComposeCompilerMetrics").orNull.toBoolean()
        val enableReports = project.providers.gradleProperty("enableComposeCompilerReports").orNull.toBoolean()

        val rootBuildDir = rootProject.layout.buildDirectory.asFile.get()
        val relativePath = projectDir.relativeTo(rootDir)

        if (enableMetrics) {
            rootBuildDir.resolve("compose-metrics").resolve(relativePath).let(metricsDestination::set)
        }

        if (enableReports) {
            rootBuildDir.resolve("compose-reports").resolve(relativePath).let(reportsDestination::set)
        }
    }
}

internal fun Project.configureTest() {
    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }
}

val Project.generatedBuildDir: File get() = project.layout.buildDirectory.asFile.get().resolve("generated/mihon")
