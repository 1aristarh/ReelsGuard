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
            // short timings for testing: 30 s budget, 1 min cooldown
            buildConfigField("long", "BUDGET_MS", "30_000L")
            buildConfigField("long", "COOLDOWN_MS", "60_000L")
        }
        release {
            buildConfigField("long", "BUDGET_MS", "600_000L")
            buildConfigField("long", "COOLDOWN_MS", "1_800_000L")
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
