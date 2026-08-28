plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.littlemachineworks.svgvectorconverter"
    compileSdk = 36

    buildFeatures {
    	buildConfig = true
    } 
    
    defaultConfig {
        applicationId = "com.littlemachineworks.svgvectorconverter"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
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
    implementation("com.android.billingclient:billing:9.1.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.vectordrawable:vectordrawable:1.2.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
