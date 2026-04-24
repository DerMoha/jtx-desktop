import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform") version "2.3.20"
    id("org.jetbrains.compose") version "1.10.3"
    kotlin("plugin.serialization") version "2.3.20"
    kotlin("plugin.compose") version "2.3.20"
}

group = "com.jtx.desktop"
version = "1.0.0"

val composeJavaHome = providers.gradleProperty("compose.java.home")
    .orElse(providers.environmentVariable("JAVA_HOME"))
    .orNull

repositories {
    mavenCentral()
    google()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.ui)
                api(compose.foundation)
                api(compose.material3)
                api("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                api(compose.components.resources)
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("com.google.code.gson:gson:2.11.0")
            }
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.skiko:skiko:0.9.47")
    }
}

compose {
    desktop {
        application {
            if (composeJavaHome != null) {
                javaHome = composeJavaHome
            }
            mainClass = "com.jtx.desktop.MainKt"
            nativeDistributions {
                targetFormats(
                    TargetFormat.Dmg,
                    TargetFormat.Exe,
                    TargetFormat.Deb,
                    TargetFormat.Rpm,
                )
                packageName = "jtx-desktop"
                packageVersion = project.version.toString()
                description = "JTX Desktop"
                vendor = "JTX"
                includeAllModules = true

                macOS {
                    bundleID = "com.jtx.desktop"
                }
            }
        }
    }
}
