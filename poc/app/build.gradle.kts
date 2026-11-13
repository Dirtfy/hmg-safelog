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
        versionCode = 1
        versionName = "0.1-poc"
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
}
