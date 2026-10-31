// SPDX-License-Identifier: AGPL-3.0-or-later
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val rustResourceDir = layout.buildDirectory.dir("generated/rustResources")
val buildRustLinuxNative = tasks.register<Exec>("buildRustLinuxNative") {
    inputs.files(
        fileTree(rootProject.projectDir.parentFile.resolve("rust/core")) { exclude("target/**") },
        rootProject.projectDir.parentFile.resolve("mobile/shared/src/nativeInterop/jni/jni_bridge.c"),
        rootProject.projectDir.parentFile.resolve("tools/build-linux-native.sh"),
    )
    outputs.dir(rustResourceDir)
    commandLine(
        "bash",
        rootProject.projectDir.parentFile.resolve("tools/build-linux-native.sh").absolutePath,
        rustResourceDir.get().asFile.absolutePath,
    )
    onlyIf { System.getProperty("os.name").lowercase().contains("linux") }
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    sourceSets {
        val jvmMain by getting {
            resources.srcDir(rustResourceDir)
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio)
            }
        }
    }
}

tasks.matching { it.name == "jvmProcessResources" }.configureEach {
    dependsOn(buildRustLinuxNative)
}

compose.desktop {
    application {
        mainClass = "org.codeberg.mobile.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "codeberg-mobile"
            packageVersion = "1.0.0"
            
            // ✅ This is where iconFile actually belongs for Linux packaging
            linux {
                iconFile.set(project.file("src/jvmMain/resources/app_icon.png"))
            }
        }
    }
}
