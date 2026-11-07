import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val ndkVersionString = "27.0.12077973"

android {
    namespace = "dev.pocketserver.app"
    compileSdk = 35
    ndkVersion = ndkVersionString

    defaultConfig {
        applicationId = "dev.pocketserver"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "0.1.0-phase1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }
        buildConfigField("String", "SUPPORTED_ABI", "\"arm64-v8a\"")
        buildConfigField("int", "POCKET_TARGET_SDK", "28")
        val catalogUrl = System.getenv("POCKETSERVER_CATALOG_URL")
            ?.trim()
            ?.ifBlank { null }
            ?: "https://raw.githubusercontent.com/emonbudc/PocketServer/main/runtime-catalog.json"
        buildConfigField(
            "String",
            "RUNTIME_CATALOG_URL",
            "\"${catalogUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
    }

    signingConfigs {
        val keystoreFile = System.getenv("POCKETSERVER_KEYSTORE_FILE")
        val keystorePassword = System.getenv("POCKETSERVER_KEYSTORE_PASSWORD")
        val keyAliasValue = System.getenv("POCKETSERVER_KEY_ALIAS")
        val keyPasswordValue = System.getenv("POCKETSERVER_KEY_PASSWORD")
        if (!keystoreFile.isNullOrBlank() && !keystorePassword.isNullOrBlank() &&
            !keyAliasValue.isNullOrBlank() && !keyPasswordValue.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        getByName("debug") {
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

    lint {
        abortOnError = true
        disable += "ExpiredTargetSdkVersion"
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/probeAssets"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val probeSource = layout.projectDirectory.file("src/main/probe/psprobe.c")
val probeAssetsDir = layout.buildDirectory.dir("generated/probeAssets")
val probeHostTag = when {
    System.getProperty("os.name").lowercase().contains("linux") -> "linux-x86_64"
    System.getProperty("os.name").lowercase().contains("mac") -> "darwin-x86_64"
    System.getProperty("os.name").lowercase().contains("windows") -> "windows-x86_64"
    else -> error("Unsupported host for NDK clang")
}

val compileNativeProbe = tasks.register<Exec>("compileNativeProbe") {
    group = "build"
    description = "Compiles the AArch64 native probe executed at runtime for Phase 1 validation."
    val sdkDirectory = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: android.sdkDirectory.absolutePath
    val clang = File(
        sdkDirectory,
        "ndk/$ndkVersionString/toolchains/llvm/prebuilt/$probeHostTag/bin/aarch64-linux-android26-clang",
    )
    val outputFile = probeAssetsDir.get().asFile.resolve("arm64-v8a/psprobe")
    inputs.file(probeSource)
    outputs.file(outputFile)
    doFirst {
        if (!clang.isFile) {
            throw GradleException(
                "NDK clang not found at ${clang.absolutePath}. Install NDK $ndkVersionString.",
            )
        }
        outputFile.parentFile.mkdirs()
    }
    commandLine(
        clang.absolutePath,
        "-O2",
        "-fPIE",
        "-pie",
        "-Wall",
        "-Wextra",
        "-Werror",
        "-o",
        outputFile.absolutePath,
        probeSource.asFile.absolutePath,
    )
}

tasks.matching { task ->
    task.name.startsWith("merge") && task.name.endsWith("Assets")
}.configureEach {
    dependsOn(compileNativeProbe)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":storage"))
    implementation(project(":runtime"))
    implementation(project(":process"))
    implementation(project(":services"))
    implementation(project(":database"))
    implementation(project(":terminal"))
    implementation(project(":network"))
    implementation(project(":ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
