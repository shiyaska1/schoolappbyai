plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.school.attendance"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shiyaska.schoolmanagement"
        minSdk = 26
        targetSdk = 36
        // CI sets VERSION_CODE per build (see .github/workflows/build.yml and release.yml) so every
        // APK/AAB gets a unique, always-increasing code without hand-editing this file each time.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    // Play Store upload key — provided by CI via env vars (kept out of git). Falls back to the
    // committed "stable" key for local/debug builds so a plain ./gradlew still works.
    val uploadStoreFile = System.getenv("UPLOAD_STORE_FILE")
    val uploadStorePassword = System.getenv("UPLOAD_STORE_PASSWORD")
    val uploadKeyAlias = System.getenv("UPLOAD_KEY_ALIAS")
    val uploadKeyPassword = System.getenv("UPLOAD_KEY_PASSWORD")
    val hasUploadKey = !uploadStoreFile.isNullOrBlank() && !uploadStorePassword.isNullOrBlank()

    signingConfigs {
        create("stable") {
            storeFile = file("keystore.jks")
            storePassword = "attendance123"
            keyAlias = "schoolattendance"
            keyPassword = "attendance123"
        }
        if (hasUploadKey) {
            create("upload") {
                storeFile = file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Sign with the committed stable key so testers can update without data loss.
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Use the Play upload key when CI provides it, else the local stable key.
            signingConfig = signingConfigs.getByName(if (hasUploadKey) "upload" else "stable")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Local offline database — students, classes, attendance all live on-device.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // QR code generation for the parent/staff setup link (encode only — no camera/scanning needed).
    implementation("com.google.zxing:core:3.5.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
