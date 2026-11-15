plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hmg.safelog.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hmg.safelog.poc"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
    }

    // Release signing: the keystore and its passwords come from GitHub Actions secrets (never committed).
    val ks = System.getenv("SAFELOG_KEYSTORE")
    signingConfigs {
        if (ks != null) create("release") {
            storeFile = file(ks)
            storePassword = System.getenv("SAFELOG_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SAFELOG_KEY_ALIAS") ?: "safelog"
            keyPassword = System.getenv("SAFELOG_KEYSTORE_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            // No minification, so the release build runs the same code as the tested debug build.
            isMinifyEnabled = false
            if (ks != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Demo mode replays the simulation CSVs straight from poc/sim/scenarios.
    sourceSets.getByName("main").assets.srcDir("../sim/scenarios")

    // AAOS CarPropertyManager (PERF_VEHICLE_SPEED) lives in the optional android.car library.
    useLibrary("android.car")
}

dependencies {
    implementation(project(":core"))
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
}
