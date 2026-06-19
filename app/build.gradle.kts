plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.svgvectorconverter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.svgvectorconverter"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17

    kotlinOptions {
        jvmTarget = "17"
    }

    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.vectordrawable:vectordrawable:1.2.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
