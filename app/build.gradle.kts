plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.crystaln.vpn"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.crystaln.vpn"
        minSdk = 24
        targetSdk = 35
        versionCode = (project.findProperty("appVersionCode") as String? ?: "1").toInt()
        versionName = project.findProperty("appVersion") as String? ?: "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Native cores (Xray, wireguard-go) ship for every ABI; per-ABI APKs keep
    // typical installs small. A universal APK is still produced.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
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

    packaging {
        jniLibs {
            // libovpnexec.so (OpenVPN core) is executed as a child process and
            // must be extracted to nativeLibraryDir, not loaded from the APK.
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            // Let Robolectric look up its Android resources/config.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // core-ktx 1.19+ требует AGP 9.1 — вернуть после апгрейда тулчейна.
    implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // BOM 2026.09+ (Compose 1.12) требует compileSdk 37 и AGP 9.1 — вернуть,
    // когда обновим тулчейн (см. также ignore в dependabot.yml).
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Haze — real hardware-accelerated backdrop blur / liquid glass
    // (frosted surfaces that blur the content behind them).
    implementation("dev.chrisbanes.haze:haze:1.6.4")
    implementation("dev.chrisbanes.haze:haze-materials:1.6.4")

    // Xray core bindings (VMess, VLESS, Trojan, Shadowsocks, SOCKS, HTTP, ...).
    implementation(files("libs/libv2ray.aar"))
    // AmneziaWG core (amneziawg-go), built from third_party/amneziawg-android.
    implementation(files("libs/amneziawg.aar"))

    // Camera + bundled QR scanner for the "import from QR code" flow
    // (bundled ML Kit works fully offline).
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // QR encoder for the "share profile" dialog (pure JVM, no Android deps).
    implementation("com.google.zxing:core:3.5.4")

    // Pure-JVM tests for parsers/config builders. Robolectric supplies the
    // android.* implementations (Uri/Base64/org.json) on the host JVM.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
}
