plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ibop.urdukeyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ibop.urdukeyboard"
        // BluetoothHidDevice (phone acting as a Bluetooth keyboard) exists from Android 9 / API 28.
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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
