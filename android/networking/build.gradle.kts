// The HTTP layer the media-server providers share: a Ktor client factory (OkHttp on Android, Darwin on iOS), the
// shared Json, and the NetworkResult every call returns (docs/architecture/ios-port/phase-3-network.md, #585).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("s2.kmp-library")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.simplecityapps.networking"
        // Its consumers still build for JVM 11 and inline NetworkResult.map, which can't inline JVM 17 bytecode
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)

            // The Retrofit stack the providers still build their services on until they move to Ktor (#585 step 3);
            // exposed as `api` because they get it from here.
            api(libs.retrofit2.retrofit)
            api(libs.retrofit2.converterMoshi)
            api(libs.moshi.kotlin)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest)
            implementation(libs.kotlinx.coroutinesTest)
            implementation(libs.ktor.client.mock)
        }
    }
}
