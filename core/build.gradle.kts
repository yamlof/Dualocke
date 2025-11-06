import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" // or your Kotlin version


}

kotlin {
    jvm()
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    sourceSets {
        commonMain.dependencies {
            // put your Multiplatform dependencies here
            implementation(kotlin("stdlib"))

            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            val ktor_version = "3.3.1"
            implementation("io.ktor:ktor-client-core:${ktor_version}")
            implementation("io.ktor:ktor-client-cio:${ktor_version}")
            implementation("io.ktor:ktor-serialization-kotlinx-json:${ktor_version}")
            implementation("io.ktor:ktor-client-content-negotiation:${ktor_version}")
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

    }
}

