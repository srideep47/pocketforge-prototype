import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.srideep.pocketforge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.srideep.pocketforge"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        ndk {
            // Single ABI: the vendored MNN and Node runtimes are arm64 only.
            abiFilters += "arm64-v8a"
        }
    }

    // Release signing comes from an untracked keystore.properties at the repo root; without it
    // assembleRelease still builds, unsigned.
    val keystoreFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreFile.isFile) {
        val props = Properties().apply { keystoreFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Installs beside a release or differently-signed build instead of replacing it,
            // so a dev install never costs the gigabytes of models the other one downloaded.
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "PocketForge Dev")
        }
        release {
            isMinifyEnabled = false
            signingConfig = releaseSigning
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        jniLibs {
            // Uncompressed + page-aligned native libs (required for 16KB page devices).
            useLegacyPackaging = false
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
        buildConfig = true
    }
}

dependencies {
    implementation(project(":engine:mnn"))
    implementation(project(":runtime:node"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.json)
}
