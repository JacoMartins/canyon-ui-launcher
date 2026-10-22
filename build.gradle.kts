plugins {
    alias(libs.plugins.android.application) apply false
    // Puts KGP 2.4.x on the classpath; AGP 9's built-in Kotlin support uses it.
    // Haze 2.0.1 is compiled against Kotlin 2.4.20.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
