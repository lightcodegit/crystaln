// CrystalN for Linux: Compose Desktop GUI + JVM core (profiles, parsers,
// config builders) + process runners for xray / wg-quick / openvpn.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    // Matches the org.json API used by the Android app (android.jar stub).
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.20")
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "com.crystaln.vpn.desktop.MainKt"
        // `./gradlew :desktop:packageDistributionForCurrentOS` → deb.
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "crystaln"
            packageVersion = "1.0.0"
            description = "Мультипротокольный VPN-клиент CrystalN"
            vendor = "CrystalN"
            modules("java.instrument", "java.management", "jdk.unsupported")
        }
    }
}
