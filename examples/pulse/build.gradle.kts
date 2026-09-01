plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.light.sdk)
}

android {
    compileSdk = rootProject.ext["compileSdk"] as Int

    defaultConfig {
        minSdk = rootProject.ext["minSdk"] as Int
        targetSdk = rootProject.ext["targetSdk"] as Int

        manifestPlaceholders["sdkVersion"] = property("sdkVersion") as String
    }

    // Public releases must NOT use the shared lightsdk-dev key below - it's checked into the
    // SDK repo with a well-known password, so anyone could re-sign a malicious APK with it and
    // have it install as a legitimate "update" over a real install. pulseRelease is a private
    // key generated locally (see docs/RELEASING.md) and never committed; release builds fall
    // back to lightsdk-dev only when that key isn't present, so other contributors can still
    // build a release variant locally without needing this specific key.
    val pulseReleaseKeystore = file("${System.getProperty("user.home")}/.android-release-keys/pulse-release.jks")
    val pulseReleasePasswordFile = file("${System.getProperty("user.home")}/.android-release-keys/pulse-release.password")

    signingConfigs {
        create("lightsdkDev") {
            storeFile = file("../../sdk/keys/lightsdk-dev.jks")
            storePassword = "android"
            keyAlias = "lightsdk-dev"
            keyPassword = "android"
            enableV3Signing = true
            enableV4Signing = true
        }
        if (pulseReleaseKeystore.exists()) {
            create("pulseRelease") {
                val password = pulseReleasePasswordFile.readText().trim()
                storeFile = pulseReleaseKeystore
                storePassword = password
                keyAlias = "pulse-release"
                keyPassword = password
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("lightsdkDev")
        }
        release {
            signingConfig = signingConfigs.getByName(
                if (pulseReleaseKeystore.exists()) "pulseRelease" else "lightsdkDev",
            )
        }
    }

    lint {
        warningsAsErrors = false
        error += "RestrictedApi"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
        targetCompatibility = JavaVersion.toVersion(rootProject.ext["jvmTarget"] as String)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(rootProject.ext["jvmTarget"] as String))
    }
}

dependencies {
    implementation(project(":sdk:client"))
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlin.test)
}
