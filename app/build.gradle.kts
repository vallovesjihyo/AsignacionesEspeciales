plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.myapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.myapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    
    // WebSockets para comunicación en tiempo real con Node.js
    // implementation("io.socket:socket.io-client:2.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    
    // Garmin ConnectIQ Mobile SDK (Descargado automáticamente desde Maven Central)
    implementation("com.garmin.connectiq:ciq-companion-app-sdk:2.2.0@aar")
    
    // CardView para la UI de Garmin
    implementation("androidx.cardview:cardview:1.0.0")
}