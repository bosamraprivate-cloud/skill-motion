import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

// Debug-only proxy settings from git-ignored local.properties (dls.proxyUrl, dls.sharedSecret).
// Release builds get empty values; real settings storage arrives in Epic 2.
val localProps =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.isFile) file.inputStream().use { load(it) }
    }

fun buildConfigString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.aitherapist.dls"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aitherapist.dls"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PROXY_URL", "\"\"")
        buildConfigField("String", "SHARED_SECRET", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "PROXY_URL", buildConfigString(localProps.getProperty("dls.proxyUrl", "")))
            buildConfigField("String", "SHARED_SECRET", buildConfigString(localProps.getProperty("dls.sharedSecret", "")))
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // MediaPipe model files must stay uncompressed so they can be memory-mapped.
    androidResources {
        noCompress += "task"
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.layout)
    implementation(libs.compose.material3.adaptive.navigation3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mediapipe.tasks.vision)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.vico.compose.m3)
    implementation(libs.konfetti.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.compose.shimmer)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.konsist)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// Core classes with a >= 90% line-coverage rule (AD-12), checked by koverVerifyCore. Geometry landed in 1.3;
// RulesEngine and the runner landed in 2.13; RepCounter arrives later.
// The class filter lives in a dedicated "core" variant so it limits only this rule, not other reports.
kover {
    currentProject {
        createVariant("core") {
            add("debug")
        }
    }
    reports {
        variant("core") {
            filters {
                includes {
                    classes(
                        "com.aitherapist.dls.vision.Geometry*",
                        "com.aitherapist.dls.vision.PoseVerifier*",
                        "com.aitherapist.dls.vision.VisibilityGate*",
                        "com.aitherapist.dls.vision.FrameCropper*",
                        "com.aitherapist.dls.scoring.RulesEngine*",
                        "com.aitherapist.dls.training.RepCounter*",
                        "com.aitherapist.dls.session.WindowRules",
                        "com.aitherapist.dls.session.Movement",
                        "com.aitherapist.dls.session.PoseSample",
                        "com.aitherapist.dls.session.AttemptWindow",
                        "com.aitherapist.dls.session.CloseReason",
                        "com.aitherapist.dls.session.PromptLadder",
                        "com.aitherapist.dls.session.PromptState",
                        "com.aitherapist.dls.session.AssessmentRunner*",
                        "com.aitherapist.dls.session.RunnerEvent*",
                        "com.aitherapist.dls.session.RunnerState*",
                        "com.aitherapist.dls.vision.NoneVerifier*"
                    )
                }
            }
            verify {
                rule("Core classes line coverage") {
                    bound {
                        minValue = 90
                        coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                    }
                }
            }
        }
    }
}

// MediaPipe models, URLs as in google-ai-edge/mediapipe-samples. Written to assets at build time
// and git-ignored. Gradle's up-to-date check skips the network when the files already exist.
val modelAssetsDir = layout.projectDirectory.dir("src/main/assets")
val mediapipeModels =
    mapOf(
        "pose_landmarker_full.task" to
            "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task",
        "hand_landmarker.task" to
            "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"
    )

abstract class DownloadModelsTask : DefaultTask() {
    @get:Input
    abstract val models: MapProperty<String, String>

    @get:Internal
    abstract val outputDir: DirectoryProperty

    @get:OutputFiles
    val outputFiles: Provider<List<File>>
        get() = outputDir.map { dir -> models.get().keys.sorted().map { dir.file(it).asFile } }

    @TaskAction
    fun download() {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        models.get().forEach { (name, url) ->
            val target = File(dir, name)
            if (target.isFile && target.length() > 0) return@forEach
            val tmp = File(dir, "$name.part")
            try {
                val connection = URI(url).toURL().openConnection()
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.getInputStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: IOException) {
                throw GradleException(
                    "downloadModels: failed to download $name from $url (${e.javaClass.simpleName}: ${e.message}). " +
                        "Check the network connection or place the file in ${dir.path} manually.",
                    e
                )
            } finally {
                tmp.delete()
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val downloadModels = tasks.register<DownloadModelsTask>("downloadModels") {
    group = "build setup"
    description = "Downloads the MediaPipe Pose and Hand landmarker models into src/main/assets."
    models.set(mediapipeModels)
    outputDir.set(modelAssetsDir)
}

tasks.named("preBuild") { dependsOn(downloadModels) }
