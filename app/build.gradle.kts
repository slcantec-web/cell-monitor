plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.cellmonitor"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.aistudio.cellmonitor.qvkpzt"
        minSdk = 30
        targetSdk = 34
        // CI passes -PappVersionCode / -PappVersionName (derived from the release tag) so the
        // installed app's versionCode matches what the update checker compares against.
        versionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("appVersionName") as String?) ?: "1.0"
    }
    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            // Release CI supplies the real (stable) key through these variables
            storePassword = System.getenv("CM_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("CM_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("CM_KEY_PASSWORD") ?: "android"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.core:core-ktx:1.13.1")
}
