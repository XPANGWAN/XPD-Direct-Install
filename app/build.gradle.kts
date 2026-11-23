plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xpd.directinstall"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xpd.directinstall"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "0.5.0"
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("XPD_KEYSTORE_PATH")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("XPD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("XPD_KEY_ALIAS")
                keyPassword = System.getenv("XPD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            val hasSigning = !System.getenv("XPD_KEYSTORE_PATH").isNullOrBlank()
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
