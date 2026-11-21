import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.kazands.networklens"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "io.github.kazands.networklens"
        minSdk = 26
        targetSdk = 37
        versionCode = 63
        versionName = "0.3.35"
    }
    buildFeatures { compose = true }
    // Match the direct build: optional library startup/profile components are unused.
    sourceSets.getByName("debug").manifest.srcFile("src/gradle/AndroidManifest.xml")
    sourceSets.getByName("release").manifest.srcFile("src/gradle/AndroidManifest.xml")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21") {
        // Nullability annotations are compiler metadata; do not package them.
        exclude(group = "org.jetbrains", module = "annotations")
    }
    compileOnly("org.jetbrains:annotations:13.0")
}
