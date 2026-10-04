plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.luogo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.luogo.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 20
        versionName = "1.0.0-foss"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEFAULT_RELAY_URL", "\"https://relay.luogo.app\"")
        buildConfigField("int", "PROTOCOL_VERSION", "1")

        ndk {
            // MapLibre's native renderer ships a .so per ABI. The x86 and x86_64 slices
            // together were 25 MB of the 51 MB release APK and are only ever used by
            // emulators. Every physical Android device is arm, so restricting to the two
            // arm ABIs roughly halves the download without excluding real hardware.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("debugConfig") {
            val ks = file("${rootDir}/debug.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        // Release signing reads the key from properties that are never committed.
        // Populate ~/.gradle/gradle.properties or pass -P flags:
        //   LUOGO_STORE_FILE, LUOGO_STORE_PASSWORD, LUOGO_KEY_ALIAS, LUOGO_KEY_PASSWORD
        create("releaseConfig") {
            val storePath = providers.gradleProperty("LUOGO_STORE_FILE").orNull
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = providers.gradleProperty("LUOGO_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("LUOGO_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("LUOGO_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            val ks = file("${rootDir}/debug.keystore")
            if (ks.exists()) {
                signingConfig = signingConfigs.getByName("debugConfig")
            }
        }
        release {
            // R8 stays fully enabled. Nothing is disabled to save bytes; only genuinely
            // unused code and resources are removed.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only sign when a real key was supplied. Otherwise the build still produces an
            // unsigned APK, which is the correct outcome rather than a debug-signed release.
            signingConfigs.getByName("releaseConfig").storeFile?.let {
                signingConfig = signingConfigs.getByName("releaseConfig")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.car.app)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.bouncycastle)
    implementation(libs.zxing.core)
    implementation(libs.maplibre.android.sdk)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
