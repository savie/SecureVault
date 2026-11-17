plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.securevault"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.securevault"
        minSdk = 26
        targetSdk = 34
        versionCode = 166
        versionName = "1.6.6"
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    flavorDimensions += "edition"
    productFlavors {
        create("free") {
            dimension = "edition"
            applicationId = "com.securevault"
        }
        create("pro") {
            dimension = "edition"
            applicationId = "com.securevault.pro"
        }
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("com.github.topjohnwu.libsu:core:5.2.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
}
