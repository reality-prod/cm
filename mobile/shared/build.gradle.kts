// SPDX-License-Identifier: AGPL-3.0-or-later
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // JVM target for Linux phones (postmarketOS, Phosh, Plasma Mobile) via linuxMobileApp.
    jvm()

    // Compiled only on macOS (Kotlin/Native for Apple needs Xcode). Skipped on Linux.
    val iosTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64())
    val rustIosTargets = mapOf(
        "iosX64" to "x86_64-apple-ios",
        "iosArm64" to "aarch64-apple-ios",
        "iosSimulatorArm64" to "aarch64-apple-ios-sim",
    )
    iosTargets.forEach { target ->
        target.compilations.getByName("main").cinterops.create("nativecore") {
            defFile(project.file("src/nativeInterop/cinterop/codeberg_core.def"))
            includeDirs(project.file("../../rust/core/include"))
        }
        target.binaries.framework {
            baseName = "Shared" // Swift: `import Shared`
            isStatic = true
            val rustTarget = rustIosTargets.getValue(target.name)
            linkerOpts(
                "-L${project.file("../../rust/core/target/$rustTarget/release").absolutePath}",
                "-lcodeberg_core",
            )
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.jetbrains.markdown)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// Fonts and other bundled resources. Accessors are generated into org.codeberg.mobile.resources.Res.
compose.resources {
    packageOfResClass = "org.codeberg.mobile.resources"
}

android {
    namespace = "org.codeberg.mobile.shared"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    val localProperties = project.file("../local.properties").takeIf { it.exists() }
    val localSdk = localProperties?.readLines()?.firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter('=')
    val sdkCandidates = listOfNotNull(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        localSdk,
        "${System.getProperty("user.home")}/Library/Android/sdk",
        "${System.getProperty("user.home")}/Android/Sdk",
    )
    val hasSdk = sdkCandidates.any { project.file(it).isDirectory }
    if (hasSdk) {
        externalNativeBuild {
            cmake {
                path = file("src/androidMain/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}

// Build the shared Rust core for each Apple architecture before Kotlin/Native links its framework.
val rustIosBuilds = mapOf(
    "iosX64" to "x86_64-apple-ios",
    "iosArm64" to "aarch64-apple-ios",
    "iosSimulatorArm64" to "aarch64-apple-ios-sim",
).map { (targetName, rustTarget) ->
    targetName to tasks.register<Exec>("buildRust${targetName.replaceFirstChar { it.uppercase() }}") {
        val rustRoot = project.file("../../rust/core")
        inputs.file(rustRoot.resolve("Cargo.toml"))
        inputs.file(rustRoot.resolve("Cargo.lock"))
        inputs.dir(rustRoot.resolve("src"))
        outputs.file(rustRoot.resolve("target/$rustTarget/release/libcodeberg_core.a"))
        commandLine(
            "cargo", "build", "--manifest-path", rustRoot.resolve("Cargo.toml").absolutePath,
            "--release", "--target", rustTarget,
        )
    }
}.toMap()

tasks.configureEach {
    val iosBuild = rustIosBuilds.entries.firstOrNull { (target, _) -> name.contains(target, ignoreCase = true) }
    if (name.startsWith("link") && name.contains("Framework", ignoreCase = true) && iosBuild != null) {
        dependsOn(iosBuild.value)
    }
}
