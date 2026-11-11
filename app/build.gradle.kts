plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.radiocoche.musica"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.radiocoche.musica"
        minSdk = 21
        targetSdk = 34
        versionCode = 4
        versionName = "0.4"
    }

    // Misma firma fija que la app Radio (archivo app/radio.keystore copiado de su repositorio).
    // Así cada APK nueva se instala encima de la anterior sin desinstalar.
    signingConfigs {
        create("coche") {
            storeFile = file("radio.keystore")
            storePassword = "radiocoche"
            keyAlias = "radio"
            keyPassword = "radiocoche"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("coche")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("coche")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Reproductor y sesión multimedia (para el launcher y los mandos del volante).
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
}
