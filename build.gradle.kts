buildscript {
    val kotlin_version by extra("1.5.30")
    val compose_version by extra("1.1.0-alpha03")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.1.0-alpha13")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.30")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.4.0-alpha10")
        classpath("com.google.gms:google-services:4.3.10")
        classpath("com.bugsnag:bugsnag-android-gradle-plugin:7.0.0-beta01")
        classpath("com.mikepenz.aboutlibraries.plugin:aboutlibraries-plugin:8.9.1")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.38.1")
        classpath("com.google.firebase:firebase-crashlytics-gradle:2.7.1")
        classpath("com.squareup.sqldelight:gradle-plugin:1.5.1")
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