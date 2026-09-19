plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Headers-only checkout of the MNN framework (llm.hpp, MNN/expr/*). The matching
// prebuilt libMNN.so is vendored in src/main/jniLibs/arm64-v8a.
val mnnSourceRoot: String = (project.findProperty("mnnSourceRoot") as? String)
    ?: error("Set mnnSourceRoot in gradle.properties (path to the MNN framework checkout)")

android {
    namespace = "com.srideep.pocketforge.engine.mnn"
    compileSdk = 35

    defaultConfig {
        minSdk = 29

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DMNN_SOURCE_ROOT=$mnnSourceRoot",
                )
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
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
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
