plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.ririxi.bunnylock.modem"
    compileSdk = 37
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = 30
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.annotation)
}
