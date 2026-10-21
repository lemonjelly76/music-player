import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    // Built on Linux for Windows with -Pwindows, so the jars carry Skia's Windows libraries (package-windows.sh).
    implementation(if (project.hasProperty("windows")) compose.desktop.windows_x64 else compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.kotlin.test)
}

// The app's jar and everything it needs at run time, for package-windows.sh to turn into a Windows app.
tasks.register<Sync>("windowsJars") {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("windows/jars"))
}

compose.desktop {
    application {
        mainClass = "io.github.lemonjelly76.musicplayer.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "AI Picks"
            packageVersion = "1.0.0"
            vendor = "lemonjelly76"
            modules("java.prefs", "java.net.http", "jdk.unsupported")
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
            windows {
                iconFile.set(project.file("icons/app.ico"))
                menu = true
                shortcut = true
                perUserInstall = true
                // Keeps upgrades replacing the installed app rather than installing beside it.
                upgradeUuid = "8a0b5f1e-3c4d-4e2a-9f6b-7d1c2e3f4a5b"
            }
        }
    }
}
