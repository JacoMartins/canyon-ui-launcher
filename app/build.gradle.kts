import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "io.github.jacomartins.canyonui"
    // compileSdk 37 is required by Haze 2.0.1's transitive AndroidX dependencies
    // (lifecycle-runtime-compose 2.11.0 declares minCompileSdk=37). It only affects
    // which APIs the compiler can see; runtime behaviour is set by targetSdk.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.jacomartins.canyonui"
        minSdk = 29
        targetSdk = 37
        versionCode = 44
        versionName = "0.4.23"
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
        abortOnError = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
        optIn.add("kotlinx.coroutines.FlowPreview")
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

    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.dynamicanimation)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.glass)

    testImplementation(libs.junit)
}
