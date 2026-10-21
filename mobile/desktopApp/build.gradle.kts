// SPDX-License-Identifier: AGPL-3.0-or-later
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.cio)
            }
        }
    }
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
