plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pairing40k.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pairing40k.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Clé de signature partagée, committée dans le dépôt : tous les builds
    // (debug comme release, quelle que soit la machine) ont la même signature,
    // donc un nouvel APK met toujours à jour l'app installée au lieu d'entrer
    // en conflit avec elle. Ne pas publier cette app sur un store avec cette
    // clé (elle est publique par construction).
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/pairing40k.jks")
            storePassword = "pairing40k"
            keyAlias = "pairing40k"
            keyPassword = "pairing40k"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
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
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")

    // Tests unitaires (JVM) des fonctions pures : solveur, import de matrices
    testImplementation("junit:junit:4.13.2")
    // org.json réel pour la JVM : les stubs d'android.jar lèvent à l'appel,
    // or le round-trip de la sauvegarde (StoreTest) en a besoin
    testImplementation("org.json:json:20231013")
}
