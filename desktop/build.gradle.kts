// CrystalN for Linux: Compose Desktop GUI + JVM core (profiles, parsers,
// config builders) + process runners for xray / wg-quick / openvpn.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
    implementation("org.json:json:20260814")
    // Pure-JVM QR encoding for the profile share dialog (Android uses ML Kit).
    implementation("com.google.zxing:core:3.5.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.20")
}

kotlin {
    jvmToolchain(21)
}

// Версия для рантайма (проверка обновлений) — из единственного источника
// gradle.properties, а не захардкоженная константа.
val appVersion: String = project.findProperty("appVersion") as String? ?: "0.1.0"
val generateVersionProperties by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/resources/version")
    outputs.dir(outDir)
    val version = appVersion
    doLast {
        val file = outDir.get().file("crystaln/version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version\n")
    }
}

sourceSets {
    main {
        resources.srcDir(generateVersionProperties)
    }
}

compose.desktop {
    application {
        mainClass = "com.crystaln.vpn.desktop.MainKt"
        // `./gradlew :desktop:packageDistributionForCurrentOS` → deb.
        nativeDistributions {
            // Форматы по платформе: jpackage умеет только «родные» пакеты.
            // ВАЖНО: targetFormats() перезаписывает список, поэтому все форматы
            // передаются одним вызовом (второй вызов молча терял deb).
            val hostOs = System.getProperty("os.name").lowercase()
            val formats = when {
                hostOs.contains("win") -> listOf(TargetFormat.Msi)
                hostOs.contains("mac") -> listOf(TargetFormat.Dmg)
                else -> listOf(TargetFormat.Deb, TargetFormat.Rpm)
            }
            targetFormats(*formats.toTypedArray())
            packageName = "crystaln"
            packageVersion = project.findProperty("appVersion") as String? ?: "0.1.0"
            // ASCII-only: WiX codepage 1252 не умеет кириллицу (LGHT0311),
            // из-за русского описания packageMsi падал на light.exe (code 311).
            description = "Multi-protocol VPN client CrystalN"
            vendor = "CrystalN"
            modules("java.instrument", "java.management", "jdk.unsupported")
            linux {
                iconFile.set(rootProject.file("desktop/src/main/resources/crystaln/icon.png"))
            }
            windows {
                iconFile.set(rootProject.file("desktop/src/main/resources/crystaln/icon.ico"))
                // Стабильный UUID: нужен jpackage для msi (иначе генерируется
                // новый и обновление ставится рядом второй копией).
                upgradeUuid = "7d3f0f0a-3e5b-4c8f-9a41-2f6b8c5d1e70"
                menuGroup = "CrystalN"
                shortcut = true
                dirChooser = true
            }
            macOS {
                iconFile.set(rootProject.file("desktop/src/main/resources/crystaln/icon.png"))
                bundleID = "com.crystaln.vpn"
            }
        }
    }
}

// Portable AppImage (any Linux, no install): builds on top of
// createDistributable; see tools/make_appimage.sh.
tasks.register<Exec>("packageAppImage") {
    group = "compose desktop"
    onlyIf { !System.getProperty("os.name").lowercase().let { it.contains("win") || it.contains("mac") } }
    dependsOn("createDistributable")
    commandLine("bash", rootProject.file("tools/make_appimage.sh").absolutePath)
    environment("VERSION", compose.desktop.application.nativeDistributions.packageVersion ?: "1.0.0")
}
