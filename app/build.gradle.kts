plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.petar.reelsguard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.petar.reelsguard"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        debug {
            // short default timings for testing: 30 s of scrolling per 2 min session
            buildConfigField("long", "BUDGET_MS", "30_000L")
            buildConfigField("long", "WINDOW_MS", "120_000L")
        }
        release {
            // default: 10 min of scrolling per 1 hour session (changeable in the app)
            buildConfigField("long", "BUDGET_MS", "600_000L")
            buildConfigField("long", "WINDOW_MS", "3_600_000L")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
