import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.kazands.networklens"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "io.github.kazands.networklens"
        minSdk = 26
        targetSdk = 37
        versionCode = 39
        versionName = "0.3.11"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_1_8) } }
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21") {
        // Nullability annotations are compiler metadata; do not package them.
        exclude(group = "org.jetbrains", module = "annotations")
    }
    compileOnly("org.jetbrains:annotations:13.0")
}
