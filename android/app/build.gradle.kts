plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.xrc.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xrc.system.service"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    dependencies {
        // Compose BOM
        implementation(platform(libs.compose.bom))
        implementation(libs.bundles.compose)
        debugImplementation(libs.bundles.compose.debug)

        // Android core
        implementation(libs.core.ktx)
        implementation(libs.activity.compose)

        // Lifecycle
        implementation(libs.bundles.lifecycle)

        // Navigation
        implementation(libs.navigation.compose)

        // Networking
        implementation(libs.okhttp)
        implementation(libs.okhttp.ws)

        // Coroutines
        implementation(libs.bundles.coroutines)

        // DataStore
        implementation(libs.datastore.preferences)

        // ML Kit OCR
        implementation(libs.mlkit.text.recognition)
    }
}
