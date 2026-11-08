plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ajyad.admin"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ajyad.admin"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        val server = (project.findProperty("SERVER_URL") as String?)?.trim().orEmpty().trimEnd('/')
        require(server.startsWith("https://")) { "SERVER_URL must start with https://" }
        buildConfigField("String", "SERVER_URL", "\"$server\"")
    }

    // A fixed key, so each new build installs over the previous one (see README).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging")
}
