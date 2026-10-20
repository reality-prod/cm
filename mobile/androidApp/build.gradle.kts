// SPDX-License-Identifier: AGPL-3.0-or-later
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "org.codeberg.mobile"
    // Built with Android 16 (API 36), targeting Android 16/17 (pass -PtargetSdk=37 to target API 37).
    compileSdk = 36

    defaultConfig {
        applicationId = providers.gradleProperty("appId").getOrElse("org.codeberg.yourusername.codebergmobile")
        minSdk = 26
        targetSdk = providers.gradleProperty("targetSdk").map(String::toInt).getOrElse(36)
        versionCode = 1
        versionName = "0.2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
}

// Release guard: refuse to build a release whose network security config trusts the system store or lacks pins.
val verifyPinning = tasks.register("verifyPinning") {
    group = "verification"
    val nsc = layout.projectDirectory.file("src/main/res/xml/network_security_config.xml")
    doLast {
        val xml = nsc.asFile.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        if (xml.contains("src=\"system\"") || xml.contains("src=\"user\"")) {
            throw GradleException("Release blocked: system/user trust anchors must not be enabled in network_security_config.xml.")
        }
        val pins = Regex("<pin digest=\"SHA-256\">([A-Za-z0-9+/]{43}=)</pin>").findAll(xml).count()
        if (pins < 3) {
            throw GradleException(
                "Release blocked: network_security_config.xml has $pins valid SPKI pins (need at least 3 CAs). " +
                    "Run tools/print-pins.sh and paste the <pin-set>.",
            )
        }
    }
}
tasks.whenTaskAdded {
    if (name == "assembleRelease" || name == "bundleRelease") dependsOn(verifyPinning)
}
