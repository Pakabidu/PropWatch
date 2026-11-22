plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uk.dx.propwatch"
    compileSdk = 34

    defaultConfig {
        applicationId = "uk.dx.propwatch"
        minSdk = 26
        targetSdk = 34
        // CI passes -PbuildNumber=<run number>; the in-app update check compares against it.
        versionCode = (findProperty("buildNumber") as String?)?.toIntOrNull() ?: 3
        versionName = "5.6"
    }

    // A fixed debug key, so each CI build installs over the last one (Android rejects
    // an update signed with a different key, and CI would otherwise make a new key every run).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
