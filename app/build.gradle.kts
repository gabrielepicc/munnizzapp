plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gabrielepiccione.onda"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gabrielepiccione.onda"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.3.1"
    }

    // Chiave di firma fissa per le build di sviluppo/CI, così gli aggiornamenti si installano sopra.
    // NON usarla per pubblicare su store: per F-Droid/Play serve una chiave privata non nel repository.
    signingConfigs {
        create("dev") {
            storeFile = file("onda-dev.keystore")
            storePassword = "onda-dev-pass"
            keyAlias = "onda"
            keyPassword = "onda-dev-pass"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Per un'installazione manuale firmiamo la release con la chiave di debug.
            // Sostituisci con una tua signingConfig prima di distribuirla.
            signingConfig = signingConfigs.getByName("dev")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
}
