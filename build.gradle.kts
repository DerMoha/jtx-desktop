import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.2.0"
    id("org.jetbrains.compose") version "1.7.3" 
    kotlin("plugin.serialization") version "2.2.0"
    kotlin("plugin.compose") version "2.2.0"
}

group = "com.jtx.desktop"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.ui)
                api(compose.foundation)
                api(compose.material3)
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

compose {
    desktop {
        application {
            mainClass = "com.jtx.desktop.MainKt"
        }
    }
}