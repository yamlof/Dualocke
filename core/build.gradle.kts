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

            val ktor_version = "3.4.0"
            api("io.ktor:ktor-client-core:${ktor_version}")
            api("io.ktor:ktor-client-cio:${ktor_version}")
            api("io.ktor:ktor-serialization-kotlinx-json:${ktor_version}")
            api("io.ktor:ktor-client-content-negotiation:${ktor_version}")
            api("io.github.jan-tennert.supabase:postgrest-kt:3.0.0")
            api("io.github.jan-tennert.supabase:auth-kt:3.0.0")
            api("io.github.jan-tennert.supabase:realtime-kt:3.0.0")
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation(libs.kotlinx.datetime)





        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

    }
}

