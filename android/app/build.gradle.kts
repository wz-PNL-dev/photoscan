plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.photoscan.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.photoscan.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    ndkVersion = "27.2.12479018"

    // Native COLMAP is opt-in: build with -PphotoscanNative -PcolmapSrc=<patched tree>
    // -PprefixPath=<deps install>. See android/native/README.md.
    if (project.hasProperty("photoscanNative")) {
        defaultConfig {
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
            externalNativeBuild {
                cmake {
                    arguments += listOf(
                        "-DCOLMAP_SRC=${project.property("colmapSrc")}",
                        "-DPREFIX_PATH=${project.property("prefixPath")}",
                        "-DANDROID_STL=c++_static",
                    )
                    cppFlags += listOf("-std=c++17")
                }
            }
        }
        externalNativeBuild {
            cmake {
                path = file("../native/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // 3D viewer: Filament-based, loads glTF/GLB, orbit/pan/zoom gestures.
    implementation("io.github.sceneview:sceneview:4.67.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
