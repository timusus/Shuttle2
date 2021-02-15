buildscript {
    val kotlin_version by extra("1.4.20")
    repositories {
        google()
        jcenter()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
    }
    dependencies {
        classpath(BuildPlugins.androidGradlePlugin)
        classpath(BuildPlugins.kotlinGradlePlugin)
        classpath(BuildPlugins.safeArgsPlugin)
        classpath(BuildPlugins.googleServicesPlugin)
        classpath(BuildPlugins.bugsnagPlugin)
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("com.android.tools.build:gradle:7.0.0-alpha06")
    }
}

plugins {
    id(BuildPlugins.gradleVersionPlugin) version (BuildPlugins.Versions.gradleVersionPlugin)
}

allprojects {
    repositories {
        google()
        jcenter()
        maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
        maven(url = "https://jitpack.io")
    }
}

tasks.register("clean").configure {
    delete("build")
}