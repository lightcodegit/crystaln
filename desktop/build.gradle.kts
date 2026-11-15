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
    // Dispatchers.Main for Compose Desktop (Swing); same version as core.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    // Matches the org.json API used by the Android app (android.jar stub).
    implementation("org.json:json:20240303")
    // Pure-JVM QR encoding for the profile share dialog (Android uses ML Kit).
    implementation("com.google.zxing:core:3.5.3")

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
            // deb нужен dpkg-deb (Debian/Ubuntu), rpm — rpmbuild (Fedora/openSUSE);
            // jpackage строит только форматы, для которых есть утилиты в PATH.
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
            )
            packageName = "crystaln"
            packageVersion = "0.1.0"
            description = "Мультипротокольный VPN-клиент CrystalN"
            vendor = "CrystalN"
            modules("java.instrument", "java.management", "jdk.unsupported")
            linux {
                iconFile.set(rootProject.file("desktop/src/main/resources/crystaln/icon.png"))
            }
        }
    }
}

// Portable AppImage (any Linux, no install): builds on top of
// createDistributable; see tools/make_appimage.sh.
tasks.register<Exec>("packageAppImage") {
    group = "compose desktop"
    dependsOn("createDistributable")
    commandLine("bash", rootProject.file("tools/make_appimage.sh").absolutePath)
    environment("VERSION", compose.desktop.application.nativeDistributions.packageVersion ?: "1.0.0")
}
