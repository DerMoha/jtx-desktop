import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3" 
    kotlin("plugin.serialization") version "2.1.0"
    kotlin("plugin.compose") version "2.1.0"
}

group = "com.jtx.desktop"
version = "0.1"

repositories {
    mavenCentral()
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
                api("androidx.compose.ui:ui:1.7.3")
                api("androidx.compose.foundation:foundation:1.7.3")
                api("androidx.compose.material3:material3:1.3.2")
                api("androidx.compose.components:components-resources:1.7.3")
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("androidx.compose.ui:ui-desktop:1.7.3")
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