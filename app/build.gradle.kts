import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.homelauncher"
    // compileSdk 37 is required by Haze 2.0.1's transitive AndroidX dependencies
    // (lifecycle-runtime-compose 2.11.0 declares minCompileSdk=37). It only affects
    // which APIs the compiler can see; runtime behaviour is set by targetSdk.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.homelauncher"
        minSdk = 29
        targetSdk = 33
        versionCode = 1
        versionName = "0.0.1-stage0"
    }

    signingConfigs {
        // Project-local debug key, committed on purpose: builds from any machine or session
        // are signed with the same key, so `adb install -r` upgrades keep the home layout.
        // Not a secret; never use it for a real release.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Optimized build for on-device performance checks, still signed with the debug key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // targetSdk 33 is a deliberate project requirement (device runs Android 13).
        disable += "OldTargetApi"
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.glass)

    testImplementation(libs.junit)
}
