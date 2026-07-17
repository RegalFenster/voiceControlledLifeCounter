plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.voicecounter.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.voicecounter.app"
        minSdk = 26
        // targetSdk 35+ forces edge-to-edge and ignores the opt-out, which pushed our
        // content under the status bar despite the insets padding; 34 restores the
        // classic behavior where the system keeps content clear of the status bar itself
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    // model files are large and already compressed internally by Vosk;
    // stop aapt from re-compressing them into the APK, it just wastes build time
    androidResources {
        noCompress += listOf("fst", "mat", "txt", "carpa", "conf", "int", "mdl", "tree", "ext")
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
}
