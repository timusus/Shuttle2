buildscript {
    val kotlin_version by extra("1.5.10")
    repositories {
        google()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.0")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.3.5")
        classpath("com.google.gms:google-services:4.3.5")
        classpath("com.bugsnag:bugsnag-android-gradle-plugin:5.7.6")
        classpath("com.mikepenz.aboutlibraries.plugin:aboutlibraries-plugin:8.8.5")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.35.1")
    }
}

plugins {
    id("com.github.ben-manes.versions") version "0.27.0"
}

allprojects {
    repositories {
        google()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
        maven(url = "https://jitpack.io")
        maven(url = "https://plugins.gradle.org/m2/")
    }
}