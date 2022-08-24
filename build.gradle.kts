buildscript {
    val kotlin_version by extra("1.7.0")
    val compose_version by extra("1.2.0-alpha08")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.3.0-rc01")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.7.0")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.5.1")
        classpath("com.google.gms:google-services:4.3.13")
        classpath("com.bugsnag:bugsnag-android-gradle-plugin:7.0.0-beta01")
        classpath("com.mikepenz.aboutlibraries.plugin:aboutlibraries-plugin:8.9.1")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.43.2")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.9.1")
        classpath("com.squareup.sqldelight:gradle-plugin:1.5.3")
    }
}

plugins {
    id("com.github.ben-manes.versions") version "0.27.0"
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
