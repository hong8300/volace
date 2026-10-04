import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release signing lives outside the repo; see keystore.properties (git-ignored).
// Without it the release build simply stays unsigned instead of failing.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.hong.volace"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hong.volace"
        minSdk = 33
        // Not 37: an app targeting 37 may change volumes from the background only in a foreground
        // service started by a user action, which rules out the schedule's alarm (DESIGN.md 8.7).
        // Side-loaded, so no store requires the latest target. compileSdk stays at 37.
        //noinspection OldTargetApi
        targetSdk = 36
        // Bump on every build handed to a device: adb then refuses to install an older APK over a
        // newer one, whose database it could not open (see VolaceDatabase).
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        // Debug builds share one key across dev machines so APKs from any of them install over
        // each other. The key is git-ignored (kept on Google Drive); copy it to app/debug.keystore.
        // Without it, the machine's own ~/.android/debug.keystore is used as usual.
        val sharedDebugKey = file("debug.keystore")
        if (sharedDebugKey.exists()) {
            getByName("debug") {
                storeFile = sharedDebugKey
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Migration tests run on the JVM under Robolectric, which reads the app's own merged assets
    // (test-source-set assets are not seen), so the exported schemas ride along in debug only.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    // Every schema version is kept in the repo so migrations can be tested against the real thing.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")

    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.room:room-testing:2.8.4")
}
